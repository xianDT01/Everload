package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.YtStreamInfoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Last-resort resolver: shells out to {@code yt-dlp}, which carries its own
 * (separately maintained) extraction logic and can succeed when every direct
 * InnerTube attempt above failed — at the cost of a much slower, heavier
 * subprocess round-trip. Anonymous mode passes no cookies; yt-dlp works fine
 * against public catalogue videos without them.
 */
@Component
@Order(30)
public class YtDlpStreamResolver implements YtStreamResolver {

    private static final Logger log = LoggerFactory.getLogger(YtDlpStreamResolver.class);

    /** Matches the desktop Safari UA yt-dlp's web client formats are bound to — using a different one 403s mid-stream. */
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.0 Safari/605.1.15";

    @Value("${ytmusic.ytdlp.enabled:true}")
    private boolean enabled;

    @Value("${ytmusic.ytdlp.binary-path:yt-dlp}")
    private String binaryPath;

    @Value("${ytmusic.ytdlp.timeout-seconds:45}")
    private int timeoutSeconds;

    @Override
    public String name() {
        return "yt-dlp";
    }

    @Override
    public YtStreamResolution resolve(String videoId) {
        if (!enabled) {
            return YtStreamResolution.failure(YtPlayabilityStatus.UNKNOWN, "resolver yt-dlp deshabilitado por configuración");
        }
        String[] cmd = {
                binaryPath,
                "-f", "bestaudio[ext=webm]/bestaudio[ext=m4a]/bestaudio",
                "--print", "%(urls)s|%(http_headers.User-Agent)s|%(filesize,filesize_approx)s|%(ext)s|%(duration)s",
                "--no-warnings", "--skip-download", "--no-playlist",
                "https://music.youtube.com/watch?v=" + videoId
        };

        String output;
        try {
            output = runProcess(cmd);
        } catch (Exception e) {
            return YtStreamResolution.failure(YtPlayabilityStatus.UNKNOWN, "yt-dlp falló: " + rootMessage(e));
        }

        String line = lastNonBlankLine(output);
        if (line == null) {
            return YtStreamResolution.failure(YtPlayabilityStatus.OTHER, "yt-dlp no devolvió ninguna línea de salida utilizable");
        }

        String[] parts = line.split("\\|", -1);
        String url = parts.length > 0 ? naToNull(parts[0]) : null;
        if (url == null) {
            // yt-dlp prints "NA" for fields it couldn't fill — typically because
            // the video itself is unavailable (deleted/private/region-locked),
            // which it reports on stderr but not in a structured field we parse.
            return YtStreamResolution.failure(YtPlayabilityStatus.OTHER,
                    "yt-dlp no encontró un formato de audio reproducible (vídeo eliminado, privado o bloqueado)");
        }
        String userAgent = parts.length > 1 ? naToNull(parts[1]) : null;
        Long contentLength = parts.length > 2 ? parseNumberOrNull(parts[2]) : null;
        String ext = parts.length > 3 ? naToNull(parts[3]) : null;
        Long durationSeconds = parts.length > 4 ? parseNumberOrNull(parts[4]) : null;
        String format = "webm".equalsIgnoreCase(ext) ? "webm" : "m4a";

        YtStreamInfoDto info = YtStreamInfoDto.builder()
                .url(url)
                .format(format)
                .userAgent(userAgent != null ? userAgent : DEFAULT_USER_AGENT)
                .contentLength(contentLength)
                .durationSeconds(durationSeconds)
                .resolvedBy(name())
                .build();
        return YtStreamResolution.success(info);
    }

    // ── Output parsing ────────────────────────────────────────────────

    private String lastNonBlankLine(String output) {
        if (output == null) {
            return null;
        }
        String[] lines = output.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String trimmed = lines[i].trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private String naToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return (trimmed.isEmpty() || trimmed.equalsIgnoreCase("NA")) ? null : trimmed;
    }

    private Long parseNumberOrNull(String s) {
        String t = naToNull(s);
        if (t == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Process execution ─────────────────────────────────────────────

    private String runProcess(String[] cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new YtMusicTransportException("yt-dlp agotó el tiempo de espera (" + timeoutSeconds + "s)");
        }
        if (process.exitValue() != 0) {
            log.warn("yt-dlp salió con código {} para una resolución de stream: {}", process.exitValue(), truncate(output));
            throw new YtMusicTransportException("yt-dlp salió con código " + process.exitValue());
        }
        return output;
    }

    private String truncate(String s) {
        if (s == null) return "";
        String oneLine = s.replace("\n", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }
}
