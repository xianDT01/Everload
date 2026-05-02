package com.EverLoad.everload.service;

import com.EverLoad.everload.model.Download;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DownloadService {

    private static final String DOWNLOADS_DIR = "./downloads/";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DownloadService.class);
    private static final Pattern YT_DLP_PROGRESS = Pattern.compile("(\\d+\\.?\\d*)%");

    @Value("${app.downloads.max-concurrent:3}")
    private int maxConcurrent;

    private Semaphore downloadSemaphore;
    private ExecutorService directDownloadExecutor;
    private final ConcurrentHashMap<String, DirectDownloadJob> directDownloadJobs = new ConcurrentHashMap<>();

    private final DownloadHistoryService downloadHistoryService;
    private final NasService nasService;
    private final MusicService musicService;

    public DownloadService(DownloadHistoryService downloadHistoryService, NasService nasService, MusicService musicService) {
        this.downloadHistoryService = downloadHistoryService;
        this.nasService = nasService;
        this.musicService = musicService;
    }

    @PostConstruct
    private void init() {
        downloadSemaphore = new Semaphore(maxConcurrent, true);
        directDownloadExecutor = Executors.newFixedThreadPool(maxConcurrent);
    }

    /** Tries to acquire a download slot. Returns false if all slots are busy. */
    private boolean acquireSlot() {
        try {
            return downloadSemaphore.tryAcquire(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }


    // ── Input validators ────────────────────────────────────────────────────
    /** YouTube video IDs are 11 chars: letters, digits, - and _ only. */
    private static boolean isValidYouTubeId(String id) {
        return id != null && id.matches("[A-Za-z0-9_\\-]{11}");
    }

    /** Allowed video resolutions (height in pixels). */
    private static final java.util.Set<String> ALLOWED_RESOLUTIONS =
            java.util.Set.of("360", "480", "720", "1080", "1440", "2160");

    /**
     * Validates that a URL belongs to a known social media domain.
     * Prevents arbitrary URLs or yt-dlp flag injection via the URL parameter.
     */
    private static boolean isAllowedMediaUrl(String url, String... allowedDomains) {
        if (url == null || url.isBlank()) return false;
        // Must start with https:// and match one of the allowed domains
        if (!url.startsWith("https://") && !url.startsWith("http://")) return false;
        for (String domain : allowedDomains) {
            if (url.contains(domain)) return true;
        }
        return false;
    }

    public ResponseEntity<FileSystemResource> downloadVideo(String videoId, String resolution) {
        if (!isValidYouTubeId(videoId)) {
            logger.warn("Rejected downloadVideo — invalid videoId: {}", videoId);
            return ResponseEntity.badRequest().build();
        }
        if (!ALLOWED_RESOLUTIONS.contains(resolution)) {
            logger.warn("Rejected downloadVideo — invalid resolution: {}", resolution);
            return ResponseEntity.badRequest().build();
        }
        try {
            String tempDir = createTempDownloadDir();
            String[] cmd = {
                "yt-dlp",
                "--print", "after_move:filepath",
                "-f", "bestvideo[height=" + resolution + "]+bestaudio/best",
                "-o", tempDir + "%(title)s.%(ext)s",
                "https://www.youtube.com/watch?v=" + videoId
            };
            downloadHistoryService.recordDownload(new Download("videoId=" + videoId, "vídeo", "YouTube"));
            return executeCommand(cmd, "vídeo", "YouTube");
        } catch (Exception e) {
            logger.error("downloadVideo error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    private static final java.util.Set<String> ALLOWED_AUDIO_FORMATS =
            java.util.Set.of("mp3", "m4a", "flac", "opus", "ogg", "wav");

    public ResponseEntity<FileSystemResource> downloadMusic(String videoId, String format) {
        if (!isValidYouTubeId(videoId)) {
            logger.warn("Rejected downloadMusic — invalid videoId: {}", videoId);
            return ResponseEntity.badRequest().build();
        }
        if (!ALLOWED_AUDIO_FORMATS.contains(format)) {
            logger.warn("Rejected downloadMusic — invalid format: {}", format);
            return ResponseEntity.badRequest().build();
        }
        try {
            String tempDir = createTempDownloadDir();
            String[] cmd = {
                "yt-dlp",
                "--ignore-errors",
                "--print", "after_move:filepath",
                "-x", "--audio-format", format, "--audio-quality", "0",
                "--embed-thumbnail",
                "--embed-metadata",
                "--parse-metadata", "%(title)s:%(meta_title)s",
                "--parse-metadata", "%(uploader)s:%(meta_artist)s",
                "--no-playlist",
                "-o", tempDir + "%(title)s.%(ext)s",
                "https://www.youtube.com/watch?v=" + videoId
            };
            downloadHistoryService.recordDownload(new Download("videoId=" + videoId, "music", "YouTube"));
            return executeCommand(cmd, "music", "YouTube");
        } catch (Exception e) {
            logger.error("downloadMusic error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public DirectDownloadJob queueMusicDownload(String videoId, String format) {
        if (!isValidYouTubeId(videoId)) {
            throw new IllegalArgumentException("ID de YouTube invalido");
        }
        String safeFormat = (format == null || format.isBlank()) ? "mp3" : format;
        if (!ALLOWED_AUDIO_FORMATS.contains(safeFormat)) {
            throw new IllegalArgumentException("Formato no permitido");
        }

        String jobId = UUID.randomUUID().toString();
        DirectDownloadJob job = new DirectDownloadJob(jobId, videoId, safeFormat);
        directDownloadJobs.put(jobId, job);
        directDownloadExecutor.submit(() -> executeQueuedMusicDownload(job));
        return job;
    }

    public DirectDownloadJob getDirectDownloadJob(String jobId) {
        return directDownloadJobs.get(jobId);
    }

    public ResponseEntity<FileSystemResource> downloadQueuedFile(String jobId) {
        DirectDownloadJob job = directDownloadJobs.get(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        if (job.status != DirectDownloadStatus.DONE || job.filePath == null || job.filePath.isBlank()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        File file = new File(job.filePath);
        if (!file.exists()) return ResponseEntity.notFound().build();
        ResponseEntity<FileSystemResource> response = sendFile(file);
        directDownloadJobs.remove(jobId);
        return response;
    }

    private void executeQueuedMusicDownload(DirectDownloadJob job) {
        if (!acquireSlot()) {
            job.status = DirectDownloadStatus.ERROR;
            job.error = "Demasiadas descargas simultaneas, intentalo de nuevo";
            job.completedAt = System.currentTimeMillis();
            return;
        }

        String tempDirPath = createTempDownloadDir();
        try {
            job.status = DirectDownloadStatus.RUNNING;
            job.progress = 5;
            String[] cmd = {
                "yt-dlp",
                "--ignore-errors",
                "--print", "after_move:filepath",
                "-x", "--audio-format", job.format, "--audio-quality", "0",
                "--embed-thumbnail",
                "--embed-metadata",
                "--parse-metadata", "%(title)s:%(meta_title)s",
                "--parse-metadata", "%(uploader)s:%(meta_artist)s",
                "--no-playlist",
                "-o", tempDirPath + "%(title)s.%(ext)s",
                "https://www.youtube.com/watch?v=" + job.videoId
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("nsig extraction failed")) continue;
                        Matcher matcher = YT_DLP_PROGRESS.matcher(line);
                        if (matcher.find()) {
                            try {
                                job.progress = Math.max(job.progress, Math.min((int) Double.parseDouble(matcher.group(1)), 94));
                            } catch (NumberFormatException ignored) {}
                        } else {
                            logger.info("yt-dlp queued music: {}", line);
                        }
                    }
                } catch (IOException ignored) {}
            });
            stderrThread.start();

            String finalPath;
            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                finalPath = outReader.readLine();
            }
            int exit = process.waitFor();
            stderrThread.join(5000);

            if (exit != 0 || finalPath == null || finalPath.isBlank()) {
                throw new RuntimeException("yt-dlp fallo o no devolvio la ruta del archivo");
            }

            File finalFile = new File(finalPath.trim());
            if (!finalFile.exists()) {
                throw new RuntimeException("Archivo temporal no encontrado: " + finalPath);
            }

            String songTitle  = finalFile.getName();
            String songArtist = "";
            int dashIdx = songTitle.indexOf(" - ");
            if (dashIdx > 0) {
                songArtist = songTitle.substring(0, dashIdx).trim();
                songTitle  = songTitle.substring(dashIdx + 3).trim();
            }
            musicService.ensureMetadata(finalFile, songTitle, songArtist);
            downloadHistoryService.recordDownload(new Download(finalFile.getName(), "music", "YouTube"));

            job.filename = finalFile.getName();
            job.filePath = finalFile.getAbsolutePath();
            job.progress = 100;
            job.status = DirectDownloadStatus.DONE;
            job.completedAt = System.currentTimeMillis();
        } catch (Exception e) {
            job.status = DirectDownloadStatus.ERROR;
            job.error = e.getMessage();
            job.completedAt = System.currentTimeMillis();
            logger.error("Queued music download failed for {}: {}", job.videoId, e.getMessage());
            cleanupTempDir(tempDirPath);
        } finally {
            downloadSemaphore.release();
        }
    }

    public ResponseEntity<?> getPlaylistVideos(String playlistUrl) {
        if (!isAllowedMediaUrl(playlistUrl, "youtube.com", "youtu.be")) {
            logger.warn("Rejected getPlaylistVideos — disallowed URL: {}", playlistUrl);
            return ResponseEntity.badRequest().body("URL de playlist no permitida");
        }
        try {
            // ProcessBuilder with explicit args — URL is a single argument, no shell injection possible
            ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp", "--flat-playlist", "--print", "%(title)s|%(id)s", playlistUrl
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            List<Map<String, String>> videos = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 2);
                if (parts.length == 2) {
                    Map<String, String> video = new HashMap<>();
                    video.put("title", parts[0]);
                    video.put("id", parts[1]);
                    videos.add(video);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error ejecutando yt-dlp");
            }
            return ResponseEntity.ok(videos);
        } catch (Exception e) {
            logger.error("getPlaylistVideos error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno");
        }
    }




    private ResponseEntity<FileSystemResource> executeCommand(String[] cmd, String tipo, String origen) {
        if (!acquireSlot()) {
            logger.warn("Download rejected — max concurrent downloads ({}) reached", maxConcurrent);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        try {
            logger.info("🔵 Ejecutando comando: {}", String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();

            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader  = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            new Thread(() -> {
                String line;
                try {
                    while ((line = errorReader.readLine()) != null) {
                        if (!line.contains("nsig extraction failed")) {
                            System.out.println("⚠️ YT-DLP ERROR: " + line);
                        }
                    }
                } catch (IOException e) { e.printStackTrace(); }
            }).start();

            int exitCode = process.waitFor();
            String finalPath = outputReader.readLine();
            outputReader.close();
            errorReader.close();

            if (exitCode != 0 || finalPath == null || finalPath.isEmpty()) {
                logger.info("❌ yt-dlp terminó con error o no devolvió la ruta final.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            File finalFile = new File(finalPath.trim());
            if (!finalFile.exists()) {
                logger.info("❌ El archivo indicado por yt-dlp no existe: {}", finalPath);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            String songTitle  = finalFile.getName();
            String songArtist = "";
            int dashIdx = songTitle.indexOf(" - ");
            if (dashIdx > 0) {
                songArtist = songTitle.substring(0, dashIdx).trim();
                songTitle  = songTitle.substring(dashIdx + 3).trim();
            }
            musicService.ensureMetadata(finalFile, songTitle, songArtist);

            downloadHistoryService.recordDownload(new Download(finalFile.getName(), tipo, origen));
            return sendFile(finalFile);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            downloadSemaphore.release();
        }
    }

    /** Builds a yt-dlp ProcessBuilder for a generic media URL download. */
    private ResponseEntity<FileSystemResource> downloadMediaUrl(String url, String tipo, String origen,
                                                                 String... allowedDomains) {
        if (!isAllowedMediaUrl(url, allowedDomains)) {
            logger.warn("Rejected {} download — disallowed URL: {}", origen, url);
            return ResponseEntity.badRequest().build();
        }
        try {
            String tempDir = createTempDownloadDir();
            String[] cmd = {
                "yt-dlp",
                "--print", "after_move:filepath",
                "-o", tempDir + "%(title)s.%(ext)s",
                url   // single argument — no shell, no injection
            };
            return executeCommand(cmd, tipo, origen);
        } catch (Exception e) {
            logger.error("downloadMediaUrl error for {}", origen, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }




    private String createTempDownloadDir() {
        String tempDirName = DOWNLOADS_DIR + "tmp-" + UUID.randomUUID();
        File tempDir = new File(tempDirName);
        if (!tempDir.exists()) tempDir.mkdirs();
        return tempDirName + "/";
    }

    private ResponseEntity<FileSystemResource> sendFile(File file) {
        HttpHeaders headers = new HttpHeaders();
        String safeName = makeAsciiSafe(file.getName());

        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");

        System.out.println("📤 Enviando archivo: " + file.getAbsolutePath() + " con header: " + safeName);
        logger.info("📤 Enviando archivo: {} con header: {}", file.getAbsolutePath(), safeName);
        FileSystemResource resource = new FileSystemResource(file);

        // Elimina el directorio temporal completo después de 5 segundos
        new Thread(() -> {
            try {
                // Wait long enough for the file to finish streaming to the client.
                // 5s was too short for large video files — using 2 hours as a safe upper bound.
                Thread.sleep(7_200_000);
                File parentDir = file.getParentFile();
                Files.walk(parentDir.toPath())
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(f -> {
                            if (f.delete()) {
                                logger.info("🧹 Eliminado: " + f.getAbsolutePath());
                                System.out.println("🧹 Eliminado: " + f.getAbsolutePath());
                            } else {
                                System.out.println("⚠️ No se pudo eliminar: " + f.getAbsolutePath());
                                logger.info("⚠️ No se pudo eliminar: " + f.getAbsolutePath());
                            }
                        });
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }).start();

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    private void cleanupTempDir(String tempDirPath) {
        try {
            Path path = Path.of(tempDirPath);
            if (!Files.exists(path)) return;
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException ignored) {}
    }

    private String makeAsciiSafe(String input) {
        input = input.replace("\"", "'");
        return input.replaceAll("[^\\p{Print}]", "_")
                .replaceAll("[\\\\/:*?\"<>|｜]", "_");
    }

    // ── Save directly to NAS ─────────────────────────────────────────────────

    public Map<String, String> saveMusicToNas(String videoId, String format, Long nasPathId, String subPath) {
        if (!acquireSlot()) {
            throw new IllegalStateException("Demasiadas descargas simultáneas, inténtalo de nuevo");
        }
        String tempDirPath = createTempDownloadDir();
        try {
            String[] cmd = {
                "yt-dlp",
                "--ignore-errors",
                "--print", "after_move:filepath",
                "-x", "--audio-format", format, "--audio-quality", "0",
                "--embed-thumbnail",
                "--embed-metadata",
                "--parse-metadata", "%(title)s:%(meta_title)s",
                "--parse-metadata", "%(uploader)s:%(meta_artist)s",
                "--no-playlist",
                "-o", tempDirPath + "%(title)s.%(ext)s",
                "https://www.youtube.com/watch?v=" + videoId
            };
            logger.info("🔵 [NAS] Ejecutando: {}", String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();

            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            new Thread(() -> {
                String line;
                try { while ((line = errReader.readLine()) != null) {
                    if (!line.contains("nsig extraction failed")) logger.warn("⚠️ yt-dlp: {}", line);
                }} catch (IOException ignored) {}
            }).start();

            BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            int exit = process.waitFor();
            String finalPath = outReader.readLine();
            outReader.close();

            if (exit != 0 || finalPath == null || finalPath.isBlank()) {
                throw new RuntimeException("yt-dlp falló o no devolvió la ruta del archivo");
            }

            File tmpFile = new File(finalPath.trim());
            if (!tmpFile.exists()) throw new RuntimeException("Archivo temporal no encontrado: " + finalPath);

            String songTitle  = tmpFile.getName();
            String songArtist = "";
            int dashIdx = songTitle.indexOf(" - ");
            if (dashIdx > 0) {
                songArtist = songTitle.substring(0, dashIdx).trim();
                songTitle  = songTitle.substring(dashIdx + 3).trim();
            }
            musicService.ensureMetadata(tmpFile, songTitle, songArtist);

            String fileName = tmpFile.getName();
            String savedPath = nasService.saveToNas(nasPathId, subPath, tmpFile.toPath(), fileName);
            downloadHistoryService.recordDownload(new Download(fileName, "music (NAS)", "YouTube"));
            logger.info("✅ [NAS] Guardado en: {}", savedPath);

            return Map.of("filename", fileName, "path", savedPath);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error al ejecutar yt-dlp: " + e.getMessage(), e);
        } finally {
            downloadSemaphore.release();
            // Clean up temp dir
            File tempDir = new File(tempDirPath).getParentFile();
            try {
                if (tempDir != null && tempDir.exists()) {
                    Files.walk(tempDir.toPath())
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            } catch (IOException ignored) {}
        }
    }

    public ResponseEntity<FileSystemResource> downloadTwitterVideo(String tweetUrl) {
        return downloadMediaUrl(tweetUrl, "vídeo", "Twitter",
                "twitter.com", "x.com", "t.co");
    }

    public ResponseEntity<FileSystemResource> downloadFacebookVideo(String videoUrl) {
        return downloadMediaUrl(videoUrl, "vídeo", "Facebook",
                "facebook.com", "fb.watch", "fb.com");
    }

    public ResponseEntity<FileSystemResource> downloadInstagramVideo(String videoUrl) {
        return downloadMediaUrl(videoUrl, "vídeo", "Instagram",
                "instagram.com", "instagr.am");
    }

    public ResponseEntity<FileSystemResource> downloadTikTokVideo(String videoUrl) {
        return downloadMediaUrl(videoUrl, "vídeo", "TikTok",
                "tiktok.com", "vm.tiktok.com");
    }
    public enum DirectDownloadStatus {
        QUEUED,
        RUNNING,
        DONE,
        ERROR
    }

    public static class DirectDownloadJob {
        public final String jobId;
        public final String videoId;
        public final String format;
        public volatile DirectDownloadStatus status = DirectDownloadStatus.QUEUED;
        public volatile int progress = 0;
        public volatile String filename;
        @JsonIgnore
        public volatile String filePath;
        public volatile String error;
        public final long createdAt = System.currentTimeMillis();
        public volatile long completedAt;

        public DirectDownloadJob(String jobId, String videoId, String format) {
            this.jobId = jobId;
            this.videoId = videoId;
            this.format = format;
        }
    }
}
