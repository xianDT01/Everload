package com.everload.everload.service;

import com.everload.everload.dto.YtStreamInfoDto;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YtMusicStreamServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void resolveRejectsBlankVideoIds() {
        YtMusicStreamService service = serviceWith();

        assertThrows(IllegalArgumentException.class, () -> service.resolveStream(null));
        assertThrows(IllegalArgumentException.class, () -> service.resolveStream("  "));
    }

    @Test
    void resolveUsesFirstSuccessAfterFailuresAndCachesIt() {
        YtStreamResolver failed = resolver("direct");
        when(failed.resolve("video-1"))
                .thenReturn(YtStreamResolution.failure(YtPlayabilityStatus.UNPLAYABLE, "geo blocked"));
        YtStreamResolver broken = resolver("broken");
        when(broken.resolve("video-1")).thenThrow(new IllegalStateException("offline"));
        YtStreamResolver success = resolver("yt-dlp");
        YtStreamInfoDto expected = stream("https://audio.test/track", "webm");
        when(success.resolve("video-1")).thenReturn(YtStreamResolution.success(expected));

        YtMusicStreamService service = serviceWith(failed, broken, success);

        assertEquals(expected, service.resolveStream("video-1"));
        assertEquals(expected, service.resolveStream("video-1"));
        verify(failed).resolve("video-1");
        verify(broken).resolve("video-1");
        verify(success).resolve("video-1");
    }

    @Test
    void totalFailureIncludesEveryResolverAndIsNotCached() {
        YtStreamResolver restricted = resolver("innertube");
        when(restricted.resolve("video-2"))
                .thenReturn(YtStreamResolution.failure(YtPlayabilityStatus.LOGIN_REQUIRED, "age gate"));
        YtStreamResolver broken = resolver("yt-dlp");
        when(broken.resolve("video-2")).thenThrow(new IllegalArgumentException());
        YtMusicStreamService service = serviceWith(restricted, broken);

        YtStreamUnavailableException error = assertThrows(
                YtStreamUnavailableException.class, () -> service.resolveStream("video-2"));

        assertEquals("video-2", error.videoId());
        assertEquals(2, error.resolverFailures().size());
        assertTrue(error.getMessage().contains("LOGIN_REQUIRED"));
        assertTrue(error.getMessage().contains("IllegalArgumentException"));
        assertThrows(YtStreamUnavailableException.class, () -> service.resolveStream("video-2"));
        verify(restricted, times(2)).resolve("video-2");
    }

    @Test
    void streamReturnsNotFoundWhenResolverHasNoUrl() throws Exception {
        YtStreamResolver resolver = resolver("empty");
        when(resolver.resolve("video-3")).thenReturn(YtStreamResolution.success(stream(" ", "m4a")));
        HttpServletResponse response = mock(HttpServletResponse.class);

        serviceWith(resolver).streamAudioToResponse("video-3", null, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    void streamProxiesRangeHeadersStatusContentTypeAndBody() throws Exception {
        byte[] audio = "audio-data".getBytes(StandardCharsets.UTF_8);
        String url = startServer(audio, 206);
        YtStreamResolver resolver = resolver("local");
        when(resolver.resolve("video-4")).thenReturn(YtStreamResolution.success(stream(url, "m4a")));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        HttpServletResponse response = responseWritingTo(sink);

        serviceWith(resolver).streamAudioToResponse("video-4", "bytes=0-9", response);

        assertEquals("audio-data", sink.toString(StandardCharsets.UTF_8));
        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setHeader("Accept-Ranges", "bytes");
        verify(response).setHeader("Content-Range", "bytes 0-9/10");
        verify(response).setContentLengthLong(audio.length);
        verify(response).setContentType("audio/webm");
    }

    @Test
    void streamForwardsUpstreamErrorsWithoutWritingBody() throws Exception {
        String url = startServer("denied".getBytes(StandardCharsets.UTF_8), 403);
        YtStreamResolver resolver = resolver("local");
        when(resolver.resolve("video-5")).thenReturn(YtStreamResolution.success(stream(url, "webm")));
        HttpServletResponse response = mock(HttpServletResponse.class);

        serviceWith(resolver).streamAudioToResponse("video-5", null, response);

        verify(response).setStatus(403);
    }

    @Test
    void contentTypeFallbacksAndClientAbortDetectionAreStable() {
        YtMusicStreamService service = serviceWith();

        assertEquals("audio/mp4", ReflectionTestUtils.invokeMethod(service, "contentTypeFor", "m4a", null));
        assertEquals("audio/webm", ReflectionTestUtils.invokeMethod(service, "contentTypeFor", "webm", ""));
        assertEquals("application/octet-stream",
                ReflectionTestUtils.invokeMethod(service, "contentTypeFor", "unknown", null));
        assertEquals("audio/aac",
                ReflectionTestUtils.invokeMethod(service, "contentTypeFor", "m4a", "audio/aac; charset=binary"));
        assertEquals(true, ReflectionTestUtils.invokeMethod(
                service, "isClientAbort", new IOException("Broken pipe")));
        assertEquals(false, ReflectionTestUtils.invokeMethod(
                service, "isClientAbort", new IOException("disk error")));
    }

    private YtMusicStreamService serviceWith(YtStreamResolver... resolvers) {
        YtMusicStreamService service = new YtMusicStreamService(List.of(resolvers));
        ReflectionTestUtils.setField(service, "streamCacheTtlSeconds", 600L);
        ReflectionTestUtils.setField(service, "cacheMaxEntries", 100);
        service.init();
        return service;
    }

    private YtStreamResolver resolver(String name) {
        YtStreamResolver resolver = mock(YtStreamResolver.class);
        when(resolver.name()).thenReturn(name);
        return resolver;
    }

    private YtStreamInfoDto stream(String url, String format) {
        return YtStreamInfoDto.builder()
                .url(url)
                .format(format)
                .userAgent("test-agent")
                .build();
    }

    private String startServer(byte[] body, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/webm; codecs=opus");
            if (status == 206) {
                exchange.getResponseHeaders().set("Content-Range", "bytes 0-9/10");
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/audio";
    }

    private HttpServletResponse responseWritingTo(ByteArrayOutputStream sink) throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener listener) {
                // This synchronous test stream does not perform asynchronous writes.
            }
            @Override public void write(int value) { sink.write(value); }
            @Override public void write(byte[] bytes, int offset, int length) { sink.write(bytes, offset, length); }
        });
        return response;
    }
}
