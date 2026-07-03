package com.EverLoad.everload.service;

import com.EverLoad.everload.model.Download;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DownloadService {

    private static final String DOWNLOADS_DIR = "./downloads/";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DownloadService.class);
    private static final Pattern YT_DLP_PROGRESS = Pattern.compile("(\\d+\\.?\\d*)%");

    /** Retención de jobs terminados antes de purgarlos del mapa (y su temp dir). */
    private static final long JOB_RETENTION_MS = TimeUnit.HOURS.toMillis(1);
    /** Margen para que el cliente termine de descargar antes de borrar el temp dir. */
    private static final long BROWSER_DOWNLOAD_CLEANUP_DELAY_HOURS = 2;

    @Value("${app.downloads.max-concurrent:3}")
    private int maxConcurrent;

    private Semaphore downloadSemaphore;
    private ExecutorService directDownloadExecutor;
    private ScheduledExecutorService cleanupScheduler;
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
    public void init() {
        downloadSemaphore = new Semaphore(maxConcurrent, true);
        directDownloadExecutor = Executors.newFixedThreadPool(maxConcurrent);
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "download-cleanup");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdown() {
        if (directDownloadExecutor != null) directDownloadExecutor.shutdownNow();
        if (cleanupScheduler != null) cleanupScheduler.shutdownNow();
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
    private static final Set<String> ALLOWED_RESOLUTIONS =
            Set.of("360", "480", "720", "1080", "1440", "2160");

    private static final Set<String> ALLOWED_AUDIO_FORMATS =
            Set.of("mp3", "m4a", "flac", "opus", "ogg", "wav");

    /**
     * Validates that a URL belongs to a known social media domain.
     * Parses the real host (no basta un contains: "https://evil.com/?x=youtube.com"
     * pasaría) and requires it to be the domain itself or a subdomain.
     */
    private static boolean isAllowedMediaUrl(String url, String... allowedDomains) {
        if (url == null || url.isBlank()) return false;
        String host;
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
            host = uri.getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        for (String domain : allowedDomains) {
            if (lower.equals(domain) || lower.endsWith("." + domain)) return true;
        }
        return false;
    }

    private static String validatedAudioFormat(String format) {
        String safeFormat = (format == null || format.isBlank()) ? "mp3" : format;
        if (!ALLOWED_AUDIO_FORMATS.contains(safeFormat)) {
            throw new IllegalArgumentException("Formato no permitido");
        }
        return safeFormat;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public ResponseEntity<FileSystemResource> downloadVideo(String videoId, String resolution) {
        if (!isValidYouTubeId(videoId)) {
            logger.warn("Rejected downloadVideo — invalid videoId: {}", videoId);
            return ResponseEntity.badRequest().build();
        }
        if (!ALLOWED_RESOLUTIONS.contains(resolution)) {
            logger.warn("Rejected downloadVideo — invalid resolution: {}", resolution);
            return ResponseEntity.badRequest().build();
        }
        String tempDir = createTempDownloadDir();
        String[] cmd = {
            "yt-dlp",
            "--js-runtimes", "node",
            "--print", "after_move:filepath",
            "-f", "bestvideo[height=" + resolution + "]+bestaudio/best",
            "-o", tempDir + "%(title)s.%(ext)s",
            "https://www.youtube.com/watch?v=" + videoId
        };
        downloadHistoryService.recordDownload(new Download("videoId=" + videoId, "vídeo", "YouTube"));
        return executeCommand(cmd, "vídeo", "YouTube");
    }

    public ResponseEntity<FileSystemResource> downloadMusic(String videoId, String format) {
        if (!isValidYouTubeId(videoId)) {
            logger.warn("Rejected downloadMusic — invalid videoId: {}", videoId);
            return ResponseEntity.badRequest().build();
        }
        if (!ALLOWED_AUDIO_FORMATS.contains(format)) {
            logger.warn("Rejected downloadMusic — invalid format: {}", format);
            return ResponseEntity.badRequest().build();
        }
        String tempDir = createTempDownloadDir();
        downloadHistoryService.recordDownload(new Download("videoId=" + videoId, "music", "YouTube"));
        return executeCommand(buildAudioCommand(videoId, format, tempDir), "music", "YouTube");
    }

    public DirectDownloadJob queueMusicDownload(String videoId, String format) {
        DirectDownloadJob job = createJob(videoId, format);
        directDownloadExecutor.submit(() -> executeQueuedMusicDownload(job));
        return job;
    }

    /**
     * Encola un guardado de música directamente en el NAS, de forma asíncrona.
     * Antes era síncrono y para audios largos (>~1h) superaba el timeout de cabeceras
     * del proxy (Caddy, 120s) → fallaba. Ahora responde al instante con un jobId y el
     * frontend consulta el progreso por polling, como en la descarga al navegador.
     */
    public DirectDownloadJob queueNasSave(String videoId, String format, Long nasPathId, String subPath) {
        if (nasPathId == null) {
            throw new IllegalArgumentException("Ruta de NAS requerida");
        }
        DirectDownloadJob job = createJob(videoId, format);
        job.nasPathId = nasPathId;
        job.nasSubPath = subPath == null ? "" : subPath;
        directDownloadExecutor.submit(() -> executeQueuedNasSave(job));
        return job;
    }

    public DirectDownloadJob getDirectDownloadJob(String jobId) {
        return directDownloadJobs.get(jobId);
    }

    public ResponseEntity<FileSystemResource> downloadQueuedFile(String jobId) {
        DirectDownloadJob job = directDownloadJobs.get(jobId);
        if (job == null) {
            logger.warn("downloadQueuedFile: job {} not found", jobId);
            return ResponseEntity.notFound().build();
        }
        if (job.status != DirectDownloadStatus.DONE || job.filePath == null || job.filePath.isBlank()) {
            logger.warn("downloadQueuedFile: job {} not ready — status={} filePath={}", jobId, job.status, job.filePath);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        File file = new File(job.filePath);
        if (!file.exists()) {
            logger.warn("downloadQueuedFile: file not found at path '{}' for job {}", job.filePath, jobId);
            return ResponseEntity.notFound().build();
        }
        logger.info("downloadQueuedFile: serving {} ({} bytes) for job {}", file.getName(), file.length(), jobId);
        ResponseEntity<FileSystemResource> response = sendFile(file);
        directDownloadJobs.remove(jobId);
        return response;
    }

    public ResponseEntity<?> getPlaylistVideos(String playlistUrl) {
        if (!isAllowedMediaUrl(playlistUrl, "youtube.com", "youtu.be")) {
            logger.warn("Rejected getPlaylistVideos — disallowed URL: {}", playlistUrl);
            return ResponseEntity.badRequest().body("URL de playlist no permitida");
        }
        try {
            // ProcessBuilder with explicit args — URL is a single argument, no shell injection possible
            ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp", "--js-runtimes", "node", "--flat-playlist", "--print", "%(title)s|%(id)s", playlistUrl
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();

            List<Map<String, String>> videos = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error ejecutando yt-dlp");
            }
            return ResponseEntity.ok(videos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno");
        } catch (Exception e) {
            logger.error("getPlaylistVideos error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno");
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

    // ── Queued jobs ───────────────────────────────────────────────────────────

    private DirectDownloadJob createJob(String videoId, String format) {
        if (!isValidYouTubeId(videoId)) {
            throw new IllegalArgumentException("ID de YouTube invalido");
        }
        String safeFormat = validatedAudioFormat(format);
        if (directDownloadExecutor == null || downloadSemaphore == null) {
            throw new IllegalStateException("El servicio de descargas no está listo aún");
        }
        pruneOldJobs();
        DirectDownloadJob job = new DirectDownloadJob(UUID.randomUUID().toString(), videoId, safeFormat);
        directDownloadJobs.put(job.jobId, job);
        return job;
    }

    /** Purga jobs terminados hace más de una hora, junto con su directorio temporal. */
    private void pruneOldJobs() {
        long cutoff = System.currentTimeMillis() - JOB_RETENTION_MS;
        directDownloadJobs.values().removeIf(job -> {
            if (job.completedAt == 0 || job.completedAt >= cutoff) return false;
            if (job.filePath != null) {
                File parent = new File(job.filePath).getParentFile();
                if (parent != null) cleanupTempDir(parent.getPath());
            }
            return true;
        });
    }

    private void executeQueuedMusicDownload(DirectDownloadJob job) {
        if (!acquireSlot()) {
            failJob(job, "Demasiadas descargas simultaneas, intentalo de nuevo");
            return;
        }
        String tempDirPath = createTempDownloadDir();
        try {
            job.status = DirectDownloadStatus.RUNNING;
            job.progress = 5;
            File finalFile = runAudioDownload(job, tempDirPath);
            downloadHistoryService.recordDownload(new Download(finalFile.getName(), "music", "YouTube"));

            job.filename = finalFile.getName();
            job.filePath = finalFile.getAbsolutePath();
            completeJob(job);
        } catch (Exception e) {
            failJob(job, e.getMessage());
            logger.error("Queued music download failed for {}: {}", job.videoId, e.getMessage());
            cleanupTempDir(tempDirPath);
        } finally {
            downloadSemaphore.release();
        }
    }

    private void executeQueuedNasSave(DirectDownloadJob job) {
        if (!acquireSlot()) {
            failJob(job, "Demasiadas descargas simultaneas, intentalo de nuevo");
            return;
        }
        String tempDirPath = createTempDownloadDir();
        try {
            job.status = DirectDownloadStatus.RUNNING;
            job.progress = 5;
            File tmpFile = runAudioDownload(job, tempDirPath);

            String fileName = tmpFile.getName();
            String savedPath = nasService.saveToNas(job.nasPathId, job.nasSubPath, tmpFile.toPath(), fileName);
            downloadHistoryService.recordDownload(new Download(fileName, "music (NAS)", "YouTube"));
            logger.info("✅ [NAS] Guardado en: {}", savedPath);

            job.filename = fileName;
            completeJob(job);
        } catch (Exception e) {
            failJob(job, e.getMessage());
            logger.error("Queued NAS save failed for {}: {}", job.videoId, e.getMessage());
        } finally {
            downloadSemaphore.release();
            cleanupTempDir(tempDirPath);
        }
    }

    private void completeJob(DirectDownloadJob job) {
        job.progress = 100;
        job.status = DirectDownloadStatus.DONE;
        job.completedAt = System.currentTimeMillis();
    }

    private void failJob(DirectDownloadJob job, String error) {
        job.status = DirectDownloadStatus.ERROR;
        job.error = error;
        job.completedAt = System.currentTimeMillis();
    }

    // ── yt-dlp execution ──────────────────────────────────────────────────────

    /** Comando yt-dlp compartido para extraer audio con metadatos y carátula embebidos. */
    private String[] buildAudioCommand(String videoId, String format, String tempDirPath) {
        return new String[]{
            "yt-dlp",
            "--js-runtimes", "node",
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
    }

    /** Descarga el audio de un job actualizando su progreso y devuelve el archivo final. */
    private File runAudioDownload(DirectDownloadJob job, String tempDirPath)
            throws IOException, InterruptedException {
        String[] cmd = buildAudioCommand(job.videoId, job.format, tempDirPath);
        return runYtDlp(cmd, p -> job.progress = Math.max(job.progress, p));
    }

    /**
     * Ejecuta yt-dlp, reporta el % de progreso leído de stderr y devuelve el archivo
     * final (con metadatos título/artista garantizados). Lanza si el proceso falla.
     */
    private File runYtDlp(String[] cmd, IntConsumer onProgress) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(cmd).start();

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("nsig extraction failed")) continue;
                    Matcher matcher = YT_DLP_PROGRESS.matcher(line);
                    if (matcher.find()) {
                        try {
                            onProgress.accept(Math.min((int) Double.parseDouble(matcher.group(1)), 94));
                        } catch (NumberFormatException ignored) {}
                    } else {
                        logger.info("yt-dlp: {}", line);
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
            throw new IOException("yt-dlp falló o no devolvió la ruta del archivo");
        }
        File file = new File(finalPath.trim());
        if (!file.exists()) {
            throw new IOException("Archivo temporal no encontrado: " + finalPath);
        }
        applyFilenameMetadata(file);
        return file;
    }

    /** Deriva "Artista - Título" del nombre del archivo y completa los metadatos que falten. */
    private void applyFilenameMetadata(File file) {
        String songTitle  = file.getName();
        String songArtist = "";
        int dashIdx = songTitle.indexOf(" - ");
        if (dashIdx > 0) {
            songArtist = songTitle.substring(0, dashIdx).trim();
            songTitle  = songTitle.substring(dashIdx + 3).trim();
        }
        musicService.ensureMetadata(file, songTitle, songArtist);
    }

    private ResponseEntity<FileSystemResource> executeCommand(String[] cmd, String tipo, String origen) {
        if (!acquireSlot()) {
            logger.warn("Download rejected — max concurrent downloads ({}) reached", maxConcurrent);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        try {
            logger.info("🔵 Ejecutando comando: {}", String.join(" ", cmd));
            File finalFile = runYtDlp(cmd, p -> {});
            downloadHistoryService.recordDownload(new Download(finalFile.getName(), tipo, origen));
            return sendFile(finalFile);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (IOException e) {
            logger.error("Download failed ({} / {}): {}", tipo, origen, e.getMessage());
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
        String tempDir = createTempDownloadDir();
        String[] cmd = {
            "yt-dlp",
            "--js-runtimes", "node",
            "--print", "after_move:filepath",
            "-o", tempDir + "%(title)s.%(ext)s",
            url   // single argument — no shell, no injection
        };
        return executeCommand(cmd, tipo, origen);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()));

        logger.info("📤 Enviando archivo: {} con header: {}", file.getAbsolutePath(), safeName);

        // El temp dir se borra tras un margen amplio para no cortar streamings de
        // archivos grandes al cliente (antes: un Thread.sleep de 2h por descarga).
        File parentDir = file.getParentFile();
        if (parentDir != null) {
            cleanupScheduler.schedule(() -> cleanupTempDir(parentDir.getPath()),
                    BROWSER_DOWNLOAD_CLEANUP_DELAY_HOURS, TimeUnit.HOURS);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(new FileSystemResource(file));
    }

    private void cleanupTempDir(String tempDirPath) {
        try {
            Path path = Path.of(tempDirPath);
            if (!Files.exists(path)) return;
            try (var walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (IOException ignored) {}
    }

    private String makeAsciiSafe(String input) {
        input = input.replace("\"", "'");
        return input.replaceAll("[^\\p{Print}]", "_")
                .replaceAll("[\\\\/:*?\"<>|｜]", "_");
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
        /** Si están presentes, el job guarda en el NAS en lugar de dejar el archivo para descargar. */
        @JsonIgnore
        public volatile Long nasPathId;
        @JsonIgnore
        public volatile String nasSubPath;

        public DirectDownloadJob(String jobId, String videoId, String format) {
            this.jobId = jobId;
            this.videoId = videoId;
            this.format = format;
        }
    }
}
