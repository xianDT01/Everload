package com.everload.everload.service;

import com.everload.everload.dto.YtStreamInfoDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class YtDlpStreamResolverTest {

    private Process process;

    @BeforeEach
    void setUp() {
        process = mock(Process.class);
    }

    @Test
    void disabledResolverDoesNotStartAProcess() {
        YtDlpStreamResolver resolver = resolver(false, "not-used");

        YtStreamResolution result = resolver.resolve("video-1");

        assertFalse(result.isSuccess());
        assertEquals(YtPlayabilityStatus.UNKNOWN, result.status());
        assertTrue(result.reason().contains("deshabilitado"));
    }

    @Test
    void successfulProcessUsesLastLineAndParsesMetadata() throws Exception {
        YtStreamResolution result;
        try (MockedConstruction<ProcessBuilder> ignored = processReturning(
                "starting resolver\nhttps://audio.test/track|Test-Agent|12345|webm|42.9", 0)) {
            result = resolver(true, "yt-dlp-test").resolve("video-2");
        }

        assertTrue(result.isSuccess());
        YtStreamInfoDto info = result.streamInfo();
        assertEquals("https://audio.test/track", info.getUrl());
        assertEquals("Test-Agent", info.getUserAgent());
        assertEquals(12345L, info.getContentLength());
        assertEquals(42L, info.getDurationSeconds());
        assertEquals("webm", info.getFormat());
        assertEquals("yt-dlp", info.getResolvedBy());
    }

    @Test
    void missingOptionalMetadataUsesFallbacks() throws Exception {
        YtStreamResolution result;
        try (MockedConstruction<ProcessBuilder> ignored = processReturning(
                "https://audio.test/track|NA|invalid|m4a|NA", 0)) {
            result = resolver(true, "yt-dlp-test").resolve("video-3");
        }

        assertTrue(result.isSuccess());
        assertEquals("m4a", result.streamInfo().getFormat());
        assertTrue(result.streamInfo().getUserAgent().contains("Safari"));
        assertNull(result.streamInfo().getContentLength());
        assertNull(result.streamInfo().getDurationSeconds());
    }

    @Test
    void emptyAndUnavailableOutputReturnStructuredFailures() throws Exception {
        YtStreamResolution empty;
        try (MockedConstruction<ProcessBuilder> ignored = processReturning("", 0)) {
            empty = resolver(true, "yt-dlp-test").resolve("video-4");
        }
        YtStreamResolution unavailable;
        try (MockedConstruction<ProcessBuilder> ignored = processReturning("NA|NA|NA|NA|NA", 0)) {
            unavailable = resolver(true, "yt-dlp-test").resolve("video-5");
        }

        assertFalse(empty.isSuccess());
        assertEquals(YtPlayabilityStatus.OTHER, empty.status());
        assertTrue(empty.reason().contains("ninguna"));
        assertFalse(unavailable.isSuccess());
        assertEquals(YtPlayabilityStatus.OTHER, unavailable.status());
        assertTrue(unavailable.reason().contains("reproducible"));
    }

    @Test
    void processStartupAndNonZeroExitAreReportedWithoutThrowing() throws Exception {
        YtStreamResolution missing = resolver(true, "definitely-missing-everload-ytdlp")
                .resolve("video-6");
        YtStreamResolution failed;
        try (MockedConstruction<ProcessBuilder> ignored = processReturning("extractor failed", 7)) {
            failed = resolver(true, "yt-dlp-test").resolve("video-7");
        }

        assertFalse(missing.isSuccess());
        assertTrue(missing.reason().contains("yt-dlp"));
        assertFalse(failed.isSuccess());
        assertTrue(failed.reason().contains("c\u00f3digo 7"));
    }

    private YtDlpStreamResolver resolver(boolean enabled, String binaryPath) {
        YtDlpStreamResolver resolver = new YtDlpStreamResolver();
        ReflectionTestUtils.setField(resolver, "enabled", enabled);
        ReflectionTestUtils.setField(resolver, "binaryPath", binaryPath);
        ReflectionTestUtils.setField(resolver, "timeoutSeconds", 5);
        return resolver;
    }

    private MockedConstruction<ProcessBuilder> processReturning(String output, int exitCode) throws Exception {
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(5, TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(exitCode);
        return mockConstruction(ProcessBuilder.class, (builder, context) -> {
            when(builder.redirectErrorStream(true)).thenReturn(builder);
            when(builder.start()).thenReturn(process);
        });
    }

}
