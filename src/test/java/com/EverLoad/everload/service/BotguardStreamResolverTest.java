package com.everload.everload.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotguardStreamResolverTest {

    @Test
    void disabledResolverAndAvailabilityReturnCleanly() {
        BotguardStreamResolver resolver = resolver(mock(YtMusicInnertubeClient.class), false);

        YtStreamResolution result = resolver.resolve("video-1");

        assertFalse(result.isSuccess());
        assertEquals(YtPlayabilityStatus.UNKNOWN, result.status());
        assertTrue(result.reason().contains("deshabilitado"));
        assertFalse(resolver.isAvailable());
        assertEquals("botguard", resolver.name());
    }

    @Test
    void missingConfiguredBinaryFallsBackWithoutThrowing() {
        YtMusicInnertubeClient client = mock(YtMusicInnertubeClient.class);
        when(client.fetchVisitorData()).thenReturn("visitor-1");
        BotguardStreamResolver resolver = resolver(client, true);
        ReflectionTestUtils.setField(resolver, "configuredBinaryPath", "definitely-missing-botguard-binary");

        YtStreamResolution result = resolver.resolve("video-2");

        assertFalse(result.isSuccess());
        assertEquals(YtPlayabilityStatus.UNKNOWN, result.status());
        assertTrue(result.reason().contains("PO token"));
        verify(client).fetchVisitorData();
    }

    @Test
    void visitorDataIsFetchedOnceAndCached() {
        YtMusicInnertubeClient client = mock(YtMusicInnertubeClient.class);
        when(client.fetchVisitorData()).thenReturn("visitor-cached");
        BotguardStreamResolver resolver = resolver(client, true);

        assertEquals("visitor-cached", ReflectionTestUtils.invokeMethod(resolver, "getOrFetchVisitorData"));
        assertEquals("visitor-cached", ReflectionTestUtils.invokeMethod(resolver, "getOrFetchVisitorData"));
        verify(client, times(1)).fetchVisitorData();
    }

    @Test
    void visitorTransportFailureIsConvertedToNull() {
        YtMusicInnertubeClient client = mock(YtMusicInnertubeClient.class);
        when(client.fetchVisitorData()).thenThrow(new YtMusicTransportException("offline"));
        BotguardStreamResolver resolver = resolver(client, true);

        assertNull(ReflectionTestUtils.invokeMethod(resolver, "getOrFetchVisitorData"));
    }

    @Test
    void tokenExtractionAcceptsJsonAliasesAndLastBareLine() {
        BotguardStreamResolver resolver = resolver(mock(YtMusicInnertubeClient.class), true);

        assertEquals("po-1", ReflectionTestUtils.invokeMethod(resolver, "extractToken", "{\"poToken\":\"po-1\"}"));
        assertEquals("po-2", ReflectionTestUtils.invokeMethod(resolver, "extractToken", "{\"token\": \"po-2\"}"));
        assertEquals("po-3", ReflectionTestUtils.invokeMethod(
                resolver, "extractToken", "{\"contentPoToken\":\"po-3\"}"));
        assertEquals("bare-token", ReflectionTestUtils.invokeMethod(
                resolver, "extractToken", "diagnostic banner\nbare-token"));
    }

    @Test
    void tokenExtractionRejectsEmptyAndMalformedOutput() {
        BotguardStreamResolver resolver = resolver(mock(YtMusicInnertubeClient.class), true);

        assertNull(ReflectionTestUtils.invokeMethod(resolver, "extractToken", (Object) null));
        assertNull(ReflectionTestUtils.invokeMethod(resolver, "extractToken", "  "));
        assertNull(ReflectionTestUtils.invokeMethod(resolver, "extractToken", "{\"unknown\":\"value\"}"));
        assertNull(ReflectionTestUtils.invokeMethod(resolver, "extractToken", "{\"token\": null}"));
    }

    @Test
    void explicitBinaryPathIsResolvedOnceAndCached() {
        BotguardStreamResolver resolver = resolver(mock(YtMusicInnertubeClient.class), true);
        ReflectionTestUtils.setField(resolver, "configuredBinaryPath", "first-binary");

        assertEquals("first-binary", ReflectionTestUtils.invokeMethod(resolver, "resolveBinaryPath"));
        ReflectionTestUtils.setField(resolver, "configuredBinaryPath", "second-binary");
        assertEquals("first-binary", ReflectionTestUtils.invokeMethod(resolver, "resolveBinaryPath"));
        assertTrue(resolver.isAvailable());
    }

    @Test
    void helperMessagesHandleLongAndNestedErrors() {
        BotguardStreamResolver resolver = resolver(mock(YtMusicInnertubeClient.class), true);
        String longMessage = "x".repeat(220);
        RuntimeException nested = new RuntimeException("outer", new IllegalStateException("root"));

        String truncated = ReflectionTestUtils.invokeMethod(resolver, "truncate", longMessage);
        assertEquals(201, truncated.length());
        assertTrue(truncated.startsWith("x".repeat(200)));
        assertEquals("root", ReflectionTestUtils.invokeMethod(resolver, "rootMessage", nested));
    }

    private BotguardStreamResolver resolver(YtMusicInnertubeClient client, boolean enabled) {
        BotguardStreamResolver resolver = new BotguardStreamResolver(client);
        ReflectionTestUtils.setField(resolver, "enabled", enabled);
        ReflectionTestUtils.setField(resolver, "configuredBinaryPath", "");
        ReflectionTestUtils.setField(resolver, "mintArgsTemplate", "generate {videoId}");
        ReflectionTestUtils.setField(resolver, "timeoutSeconds", 1);
        return resolver;
    }
}
