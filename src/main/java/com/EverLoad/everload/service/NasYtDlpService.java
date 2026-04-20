package com.EverLoad.everload.service;

import com.EverLoad.everload.model.Download;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
public class NasYtDlpService {

    private static final Logger log = LoggerFactory.getLogger(NasYtDlpService.class);
    private static final Pattern PCT = Pattern.compile("(\\d+\\.?\\d*)%");
    private static final String TEMP_BASE = "./downloads/ytdlp-nas/";

    @Value("${app.downloads.max-concurrent:3}")
    private int maxConcurrent;

    private final ConcurrentHashMap<String, YtDlpJob> jobs = new ConcurrentHashMap<>();
    private ExecutorService executor;

    private final NasService nasService;
    private final DownloadHistoryService downloadHistoryService;

    public NasYtDlpService(NasService nasService, DownloadHistoryService downloadHistoryService) {
        this.nasService = nasService;
        this.downloadHistoryService = downloadHistoryService;
    }

    @PostConstruct
    void init() {
        executor = Executors.newFixedThreadPool(maxConcurrent);
        new File(TEMP_BASE).mkdirs();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public String queue(String videoId, String title, Long nasPathId, String subPath, String format) {
        String jobId = UUID.randomUUID().toString();
        String safeTitle = (title != null && !title.isBlank()) ? title : videoId;
        String safeFormat = (format != null && !format.isBlank()) ? format : "mp3";
        String safeSub = (subPath != null) ? subPath : "";
        YtDlpJob job = new YtDlpJob(jobId, videoId, safeTitle, nasPathId, safeSub, safeFormat);
        jobs.put(jobId, job);
        executor.submit(() -> execute(job));
        log.info("Queued yt-dlp job {} video={} path={}/{}", jobId, videoId, nasPathId, safeSub);
        return jobId;
    }

    public YtDlpJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    public List<YtDlpJob> getActiveJobs() {
        long cutoff = System.currentTimeMillis() - 3_600_000L; // 1 hora
        return jobs.values().stream()
                .filter(j -> j.createdAt > cutoff)
                .sorted(Comparator.comparingLong((YtDlpJob j) -> j.createdAt).reversed())
                .collect(Collectors.toList());
    }

    // ── Execution ─────────────────────────────────────────────────────────────

    private void execute(YtDlpJob job) {
        job.status = YtDlpJob.Status.RUNNING;
        String tempDir = TEMP_BASE + "tmp-" + job.jobId + "/";
        new File(tempDir).mkdirs();
        try {
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
                "-o", tempDir + "%(title)s.%(ext)s",
                "https://www.youtube.com/watch?v=" + job.videoId
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();

            // Parse progress % from stderr without blocking stdout
            Thread stderrThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.contains("nsig extraction failed")) continue;
                        Matcher m = PCT.matcher(line);
                        if (m.find()) {
                            try { job.progress = Math.min((int) Double.parseDouble(m.group(1)), 94); }
                            catch (NumberFormatException ignored) {}
                        }
                    }
                } catch (IOException ignored) {}
            });
            stderrThread.start();

            // Final path from stdout
            String finalPath;
            try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                finalPath = out.readLine();
            }
            int exit = process.waitFor();
            stderrThread.join(5000);

            if (exit != 0 || finalPath == null || finalPath.isBlank()) {
                fail(job, "yt-dlp falló (código " + exit + "). ¿El vídeo está disponible?");
                return;
            }

            File tmp = new File(finalPath.trim());
            if (!tmp.exists()) { fail(job, "Archivo no encontrado: " + finalPath); return; }

            job.progress = 97;
            String saved = nasService.saveToNas(job.nasPathId, job.subPath, tmp.toPath(), tmp.getName());
            downloadHistoryService.recordDownload(new Download(tmp.getName(), "music (NAS)", "YouTube"));

            job.resultFilename = tmp.getName();
            job.resultPath = saved;
            job.progress = 100;
            job.completedAt = System.currentTimeMillis();
            job.status = YtDlpJob.Status.DONE;
            log.info("✅ job {} → {}", job.jobId, saved);

        } catch (Exception e) {
            fail(job, e.getMessage());
            log.error("❌ job {} failed: {}", job.jobId, e.getMessage());
        } finally {
            cleanup(tempDir);
        }
    }

    private void fail(YtDlpJob job, String error) {
        job.error = error;
        job.status = YtDlpJob.Status.ERROR;
        job.completedAt = System.currentTimeMillis();
    }

    private void cleanup(String dir) {
        try {
            Path p = Path.of(dir);
            if (Files.exists(p))
                Files.walk(p).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {}
    }

    // ── Job model ─────────────────────────────────────────────────────────────

    public static class YtDlpJob {
        public enum Status { QUEUED, RUNNING, DONE, ERROR }

        public final String jobId;
        public final String videoId;
        public final String title;
        public final long nasPathId;
        public final String subPath;
        public final String format;
        public volatile Status status = Status.QUEUED;
        public volatile int progress = 0;
        public volatile String error;
        public volatile String resultFilename;
        public volatile String resultPath;
        public final long createdAt = System.currentTimeMillis();
        public volatile long completedAt;

        public YtDlpJob(String jobId, String videoId, String title,
                        long nasPathId, String subPath, String format) {
            this.jobId = jobId;
            this.videoId = videoId;
            this.title = title;
            this.nasPathId = nasPathId;
            this.subPath = subPath;
            this.format = format;
        }
    }
}
