package com.EverLoad.everload.service;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Caché HLS para audios largos (sesiones/mixes) de la biblioteca NAS: transcodifica
 * con ffmpeg a segmentos AAC y sirve playlist + segmentos ya troceados, para que el
 * navegador pueda hacer seek sin descargar el archivo entero.
 *
 * Extraído de MusicService, que mezclaba esto con indexado, carátulas, búsqueda…
 */
@Service
@RequiredArgsConstructor
public class HlsStreamService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(HlsStreamService.class);
    private static final Pattern HLS_SEGMENT_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.(ts|m4s|aac|vtt)");
    private static final String STATUS_READY = "READY";
    private static final String HLS_PLAYLIST_FILE = "index.m3u8";

    private final NasService nasService;

    @Value("${music.hls.cache-dir:./hls-cache}")
    private String hlsCacheDir;

    @Value("${music.hls.min-duration-seconds:1200}")
    private int hlsMinDurationSeconds;

    @Value("${music.hls.min-size-bytes:83886080}")
    private long hlsMinSizeBytes;

    @Value("${everload.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

    private final Map<String, HlsCacheJob> hlsJobs = new ConcurrentHashMap<>();
    private final ExecutorService hlsExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "everload-hls-cache");
        t.setDaemon(true);
        return t;
    });

    // ── Public API ────────────────────────────────────────────────────────────

    public Map<String, Object> prepareHlsStream(Long pathId, String relativePath) {
        File file = resolveFile(pathId, relativePath);
        HlsCacheJob job = buildHlsJob(pathId, relativePath, file);

        if (!job.eligible) {
            return hlsJobResponse(job);
        }

        if (isHlsReady(job)) {
            job.status = STATUS_READY;
            job.progress = 100;
            job.error = null;
            return hlsJobResponse(job);
        }

        if (!"RUNNING".equals(job.status)) {
            startHlsJob(job, file);
        }

        return hlsJobResponse(job);
    }

    public Map<String, Object> getHlsStatus(Long pathId, String relativePath) {
        File file = resolveFile(pathId, relativePath);
        HlsCacheJob job = buildHlsJob(pathId, relativePath, file);
        if (isHlsReady(job)) {
            job.status = STATUS_READY;
            job.progress = 100;
            job.error = null;
        }
        return hlsJobResponse(job);
    }

    public String getHlsPlaylist(Long pathId, String relativePath, String token) throws IOException {
        File file = resolveFile(pathId, relativePath);
        HlsCacheJob job = buildHlsJob(pathId, relativePath, file);
        if (!isHlsReady(job)) {
            throw new IllegalStateException("HLS todavia no esta preparado");
        }

        String playlist = Files.readString(job.dir.resolve(HLS_PLAYLIST_FILE), StandardCharsets.UTF_8);
        String pathIdParam = String.valueOf(pathId);
        String subPathParam = encodeUrl(relativePath);
        String tokenParam = token != null && !token.isBlank() ? "&token=" + encodeUrl(token) : "";

        return Arrays.stream(playlist.split("\\R", -1))
                .map(line -> {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return line;
                    return "/api/music/hls/segment?pathId=" + pathIdParam
                            + "&subPath=" + subPathParam
                            + "&segment=" + encodeUrl(trimmed)
                            + tokenParam;
                })
                .collect(Collectors.joining("\n"));
    }

    public void streamHlsSegmentToResponse(Long pathId, String relativePath, String segment,
                                           HttpServletResponse response) throws IOException {
        File file = resolveFile(pathId, relativePath);
        HlsCacheJob job = buildHlsJob(pathId, relativePath, file);
        if (!isHlsReady(job)) throw new IllegalStateException("HLS no preparado");
        if (segment == null || !HLS_SEGMENT_NAME.matcher(segment).matches()) {
            throw new SecurityException("Segmento HLS invalido");
        }

        Path segmentPath = job.dir.resolve(segment).normalize();
        if (!segmentPath.startsWith(job.dir) || !Files.exists(segmentPath) || !Files.isRegularFile(segmentPath)) {
            throw new IllegalArgumentException("Segmento HLS no encontrado");
        }

        response.setHeader("Cache-Control", "private, max-age=86400");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType(segment.endsWith(".aac") ? "audio/aac" : "video/mp2t");
        response.setContentLengthLong(Files.size(segmentPath));

        try (OutputStream out = response.getOutputStream()) {
            Files.copy(segmentPath, out);
            out.flush();
        } catch (IOException e) {
            if (isClientAbort(e)) return;
            throw e;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private HlsCacheJob buildHlsJob(Long pathId, String relativePath, File file) {
        String key = hlsCacheKey(pathId, relativePath, file);
        return hlsJobs.computeIfAbsent(key, ignored -> {
            int duration = readDurationSeconds(file);
            boolean eligible = duration >= hlsMinDurationSeconds || file.length() >= hlsMinSizeBytes;
            Path dir = Path.of(hlsCacheDir).resolve(key).normalize();
            HlsCacheJob job = new HlsCacheJob();
            job.key = key;
            job.dir = dir;
            job.durationSeconds = duration;
            job.fileSizeBytes = file.length();
            job.eligible = eligible;
            job.status = initialHlsStatus(eligible, dir);
            job.progress = STATUS_READY.equals(job.status) ? 100 : 0;
            return job;
        });
    }

    private String initialHlsStatus(boolean eligible, Path dir) {
        if (!eligible) return "DIRECT";
        return Files.exists(dir.resolve(HLS_PLAYLIST_FILE)) ? STATUS_READY : "IDLE";
    }

    private Map<String, Object> hlsJobResponse(HlsCacheJob job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("key", job.key);
        body.put("eligible", job.eligible);
        body.put("status", job.status);
        body.put("ready", STATUS_READY.equals(job.status));
        body.put("progress", job.progress);
        body.put("durationSeconds", job.durationSeconds);
        body.put("fileSizeBytes", job.fileSizeBytes);
        if (job.error != null && !job.error.isBlank()) body.put("error", job.error);
        return body;
    }

    private boolean isHlsReady(HlsCacheJob job) {
        return job.eligible && Files.exists(job.dir.resolve(HLS_PLAYLIST_FILE));
    }

    private void startHlsJob(HlsCacheJob job, File file) {
        job.status = "RUNNING";
        job.progress = Math.max(job.progress, 5);
        job.error = null;

        hlsExecutor.submit(() -> {
            Path tmpDir = Path.of(hlsCacheDir).resolve(job.key + ".tmp").normalize();
            try {
                deleteDirectory(tmpDir);
                Files.createDirectories(tmpDir);

                Path playlist = tmpDir.resolve(HLS_PLAYLIST_FILE);
                Path segmentPattern = tmpDir.resolve("seg_%05d.ts");
                List<String> cmd = Arrays.asList(
                        ffmpegPath, "-y",
                        "-i", file.getAbsolutePath(),
                        "-vn",
                        "-map", "0:a:0",
                        "-c:a", "aac",
                        "-b:a", "160k",
                        "-ac", "2",
                        "-ar", "44100",
                        "-f", "hls",
                        "-hls_time", "6",
                        "-hls_playlist_type", "vod",
                        "-hls_flags", "independent_segments",
                        "-hls_segment_filename", segmentPattern.toString(),
                        playlist.toString()
                );

                runHlsFfmpeg(cmd, job);

                if (!Files.exists(playlist)) {
                    throw new IOException("ffmpeg no genero la playlist HLS");
                }

                deleteDirectory(job.dir);
                Files.createDirectories(job.dir.getParent());
                Files.move(tmpDir, job.dir, StandardCopyOption.REPLACE_EXISTING);
                job.status = STATUS_READY;
                job.progress = 100;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                job.status = "FAILED";
                job.progress = 0;
                job.error = "Preparacion HLS interrumpida";
                try { deleteDirectory(tmpDir); } catch (IOException cleanupError) {
                    log.debug("Could not clean interrupted HLS temp dir {}", tmpDir, cleanupError);
                }
                log.warn("HLS cache interrupted for {}", file.getName());
            } catch (Exception e) {
                job.status = "FAILED";
                job.progress = 0;
                job.error = e.getMessage();
                try { deleteDirectory(tmpDir); } catch (IOException cleanupError) {
                    log.debug("Could not clean failed HLS temp dir {}", tmpDir, cleanupError);
                }
                log.warn("HLS cache failed for {}: {}", file.getName(), e.getMessage());
            }
        });
    }

    private void runHlsFfmpeg(List<String> cmd, HlsCacheJob job) throws IOException, InterruptedException {
        log.info("Preparing HLS stream: {}", String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                updateHlsProgressFromFfmpeg(line, job);
                log.debug("hls-ffmpeg: {}", line);
            }
        }

        int exit;
        try {
            exit = process.waitFor();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }
        if (exit != 0) {
            throw new IOException("ffmpeg termino con codigo " + exit);
        }
    }

    private void updateHlsProgressFromFfmpeg(String line, HlsCacheJob job) {
        int idx = line.indexOf("time=");
        if (idx < 0 || job.durationSeconds <= 0) return;
        int end = line.indexOf(' ', idx + 5);
        String time = line.substring(idx + 5, end > idx ? end : line.length()).trim();
        int seconds = parseFfmpegTimeSeconds(time);
        if (seconds <= 0) return;
        int pct = Math.max(5, Math.min(95, (int) ((seconds * 100.0) / job.durationSeconds)));
        job.progress = Math.max(job.progress, pct);
    }

    private int parseFfmpegTimeSeconds(String value) {
        try {
            String[] parts = value.split(":");
            if (parts.length != 3) return 0;
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            double seconds = Double.parseDouble(parts[2]);
            return (int) Math.floor(hours * 3600 + minutes * 60 + seconds);
        } catch (Exception e) {
            return 0;
        }
    }

    private int readDurationSeconds(File file) {
        try {
            AudioFile af = AudioFileIO.read(file);
            return af.getAudioHeader().getTrackLength();
        } catch (Exception e) {
            return 0;
        }
    }

    private String hlsCacheKey(Long pathId, String relativePath, File file) {
        String raw = pathId + "|" + relativePath + "|" + file.lastModified() + "|" + file.length();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, 16); i++) {
                out.append(String.format("%02x", hash[i]));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private File resolveFile(Long pathId, String relativePath) {
        Path target = nasService.resolveValidatedPath(pathId, relativePath);
        File file = target.toFile();
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            throw new IllegalArgumentException("Archivo no accesible: " + relativePath);
        }
        return file;
    }

    private String encodeUrl(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            List<Path> sorted = paths
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path path : sorted) {
                Files.deleteIfExists(path);
            }
        }
    }

    private boolean isClientAbort(IOException e) {
        String className = e.getClass().getName();
        String message = Optional.ofNullable(e.getMessage()).orElse("").toLowerCase(Locale.ROOT);
        return className.contains("ClientAbortException")
                || message.contains("broken pipe")
                || message.contains("connection reset")
                || message.contains("forcibly closed")
                || message.contains("abort")
                || message.contains("anulada")
                || message.contains("restablecida")
                || message.contains("cerrada");
    }

    private static class HlsCacheJob {
        String key;
        Path dir;
        String status;
        int progress;
        int durationSeconds;
        long fileSizeBytes;
        boolean eligible;
        String error;
    }
}
