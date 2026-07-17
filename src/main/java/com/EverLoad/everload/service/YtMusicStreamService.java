package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.YtStreamInfoDto;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the registered {@link YtStreamResolver} strategies in order
 * (Spring sorts the injected list by {@code @Order}: Botguard → InnerTube
 * fallback chain → yt-dlp) and stops at the first success.
 *
 * <p>This is the seam the user explicitly asked to be decoupled: adding a
 * new resolution method later means writing one more {@code @Component}
 * implementing {@link YtStreamResolver} with an {@code @Order} — nothing
 * here changes. Every failure along the way is collected with its
 * {@link YtPlayabilityStatus} and reason so a total failure can explain
 * itself (restricted / geo-blocked / deleted / age-gated / Botguard error /
 * yt-dlp exhausted) instead of surfacing as a bare 500.
 *
 * <p>Resolution outcomes are cached per video id: the audio proxy
 * ({@code streamAudioToResponse}) is hit once per HTTP range request the
 * browser makes for the same track (initial probe, seeks, re-buffers...),
 * and re-running the whole resolver chain — including minting a fresh
 * Botguard PO token or shelling out to yt-dlp — on every single one of
 * those would make playback start (and seeking) noticeably slow. Failures
 * are deliberately not cached so a transient hiccup doesn't stick around
 * for the whole TTL.
 */
@Service
public class YtMusicStreamService {

    private static final Logger log = LoggerFactory.getLogger(YtMusicStreamService.class);
    private static final int STREAM_BUFFER_SIZE_BYTES = 64 * 1024;

    private final List<YtStreamResolver> resolvers;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private YtMusicCache<String, YtStreamInfoDto> streamCache;

    @Value("${ytmusic.stream.cache-ttl-seconds:1800}")
    private long streamCacheTtlSeconds;

    @Value("${ytmusic.cache.max-entries:200}")
    private int cacheMaxEntries;

    public YtMusicStreamService(List<YtStreamResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    @PostConstruct
    void init() {
        streamCache = new YtMusicCache<>(streamCacheTtlSeconds * 1000, cacheMaxEntries);
    }

    public YtStreamInfoDto resolveStream(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalArgumentException("videoId requerido");
        }
        return streamCache.getOrCompute(videoId, () -> resolveUncached(videoId));
    }

    private YtStreamInfoDto resolveUncached(String videoId) {
        List<String> failures = new ArrayList<>();
        for (YtStreamResolver resolver : resolvers) {
            YtStreamResolution result;
            try {
                result = resolver.resolve(videoId);
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("Resolver {} lanzó una excepción inesperada para {}: {}", resolver.name(), videoId, message);
                failures.add(resolver.name() + ": excepción inesperada (" + message + ")");
                continue;
            }
            if (result.isSuccess()) {
                log.info("Stream de {} resuelto por '{}'", videoId, resolver.name());
                return result.streamInfo();
            }
            log.debug("Resolver {} no pudo resolver {}: {} ({})",
                    resolver.name(), videoId, result.status(), result.reason());
            failures.add(result.describe(resolver.name()));
        }
        throw new YtStreamUnavailableException(videoId, failures);
    }

    public void streamAudioToResponse(String videoId, String rangeHeader, HttpServletResponse response)
            throws IOException, InterruptedException {
        YtStreamInfoDto stream = resolveStream(videoId);
        if (stream.getUrl() == null || stream.getUrl().isBlank()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(stream.getUrl()))
                .timeout(Duration.ofMinutes(2))
                .GET()
                .header("User-Agent", Optional.ofNullable(stream.getUserAgent()).orElse("Mozilla/5.0"))
                .header("Accept", "*/*");

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            requestBuilder.header("Range", rangeHeader);
        }

        HttpResponse<InputStream> upstream = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        int status = upstream.statusCode();
        if (status >= 400) {
            response.setStatus(status);
            closeQuietly(upstream.body());
            return;
        }

        response.setStatus(status == HttpServletResponse.SC_PARTIAL_CONTENT
                ? HttpServletResponse.SC_PARTIAL_CONTENT
                : HttpServletResponse.SC_OK);
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Cache-Control", "private, max-age=300");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType(contentTypeFor(stream.getFormat(), upstream.headers().firstValue("content-type").orElse(null)));

        upstream.headers().firstValue("content-range").ifPresent(v -> response.setHeader("Content-Range", v));
        upstream.headers().firstValue("content-length").ifPresent(v -> {
            try {
                response.setContentLengthLong(Long.parseLong(v));
            } catch (NumberFormatException ignored) {
                // A malformed optional length must not prevent streaming the upstream body.
            }
        });

        try (InputStream in = upstream.body(); OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (IOException e) {
            if (isClientAbort(e)) return;
            throw e;
        }
    }

    private String contentTypeFor(String format, String upstreamContentType) {
        if (upstreamContentType != null && !upstreamContentType.isBlank()) {
            return upstreamContentType.split(";", 2)[0];
        }
        if ("m4a".equalsIgnoreCase(format) || "mp4".equalsIgnoreCase(format)) {
            return "audio/mp4";
        }
        if ("webm".equalsIgnoreCase(format)) {
            return "audio/webm";
        }
        return "application/octet-stream";
    }

    private boolean isClientAbort(IOException e) {
        String className = e.getClass().getName();
        String message = Optional.ofNullable(e.getMessage()).orElse("").toLowerCase();
        return className.contains("ClientAbortException")
                || message.contains("broken pipe")
                || message.contains("connection reset")
                || message.contains("forcibly closed")
                || message.contains("abort")
                || message.contains("anulada")
                || message.contains("restablecida")
                || message.contains("cerrada");
    }

    private void closeQuietly(InputStream in) {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {
            // Closing a rejected upstream response is best effort.
        }
    }
}
