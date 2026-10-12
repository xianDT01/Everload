package com.everload.everload.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InnertubeChainStreamResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void walksFallbackClientsUntilAPlayableUnsignedStreamIsFound() throws Exception {
        YtMusicInnertubeClient client = mock(YtMusicInnertubeClient.class);
        InnertubeChainStreamResolver resolver = new InnertubeChainStreamResolver(client);
        JsonNode loginRequired = mapper.readTree("""
                {"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Sign in"}}
                """);
        JsonNode playable = mapper.readTree("""
                {"playabilityStatus":{"status":"OK"},"videoDetails":{"lengthSeconds":"123"},
                 "streamingData":{"adaptiveFormats":[
                   {"mimeType":"audio/mp4","url":"https://audio.test/track.m4a"}
                 ]}}
                """);
        when(client.player(YtMusicClient.MAIN_CLIENT, "video-id", null, null)).thenReturn(loginRequired);
        when(client.player(YtMusicClient.ANDROID_VR_1_43_32, "video-id", null, null)).thenReturn(playable);

        YtStreamResolution result = resolver.resolve("video-id");

        assertTrue(result.isSuccess());
        assertEquals("https://audio.test/track.m4a", result.streamInfo().getUrl());
        assertEquals(123L, result.streamInfo().getDurationSeconds());
        assertEquals("innertube-chain", resolver.name());
        verify(client).player(YtMusicClient.ANDROID_VR_1_43_32, "video-id", null, null);
    }

    @Test
    void continuesAfterTransportFailuresAndReturnsLastFailureReason() throws Exception {
        YtMusicInnertubeClient client = mock(YtMusicInnertubeClient.class);
        InnertubeChainStreamResolver resolver = new InnertubeChainStreamResolver(client);
        JsonNode unavailable = mapper.readTree("""
                {"playabilityStatus":{"status":"UNPLAYABLE","reason":"Video removed"}}
                """);
        when(client.player(YtMusicClient.MAIN_CLIENT, "video-id", null, null))
                .thenThrow(new YtMusicTransportException("primary offline"));
        when(client.player(YtMusicClient.ANDROID_VR_1_43_32, "video-id", null, null))
                .thenReturn(unavailable);
        when(client.player(YtMusicClient.ANDROID_VR_1_61_48, "video-id", null, null))
                .thenThrow(new YtMusicTransportException("last client offline"));

        YtStreamResolution result = resolver.resolve("video-id");

        assertFalse(result.isSuccess());
        assertEquals(YtPlayabilityStatus.UNKNOWN, result.status());
        assertEquals("ANDROID_VR: last client offline", result.reason());
        verify(client).player(YtMusicClient.ANDROID_VR_1_61_48, "video-id", null, null);
    }

    @Test
    void returnsFinalPlayabilityStatusWhenEveryClientRespondsWithoutAStream() throws Exception {
        YtMusicInnertubeClient client = mock(YtMusicInnertubeClient.class);
        InnertubeChainStreamResolver resolver = new InnertubeChainStreamResolver(client);
        JsonNode unavailable = mapper.readTree("""
                {"playabilityStatus":{"status":"UNPLAYABLE","reason":"Not available"}}
                """);
        when(client.player(YtMusicClient.MAIN_CLIENT, "video-id", null, null)).thenReturn(unavailable);
        when(client.player(YtMusicClient.ANDROID_VR_1_43_32, "video-id", null, null)).thenReturn(unavailable);
        when(client.player(YtMusicClient.ANDROID_VR_1_61_48, "video-id", null, null)).thenReturn(unavailable);

        YtStreamResolution result = resolver.resolve("video-id");

        assertFalse(result.isSuccess());
        assertEquals(YtPlayabilityStatus.UNPLAYABLE, result.status());
        assertEquals("Not available", result.reason());
    }
}
