package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.HlsStreamService;
import com.EverLoad.everload.service.MusicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicControllerLyricsTest {

    @TempDir
    Path tempDir;

    private MusicService musicService;
    private RestTemplate restTemplate;
    private MusicController controller;

    @BeforeEach
    void setUp() {
        musicService = mock(MusicService.class);
        restTemplate = mock(RestTemplate.class);
        controller = new MusicController(musicService, mock(HlsStreamService.class), restTemplate);
    }

    @Test
    void getLyricsReturnsSyncedLrclibResult() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("syncedLyrics", "[00:01]Line")));

        ResponseEntity<?> response = controller.getLyrics(-1L, "", "Song", "Artist", 180, "ytmusic");

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("lrclib", body.get("source"));
        assertEquals("[00:01]Line", body.get("lrc"));
    }

    @Test
    void getLyricsFallsBackToLyricsOvhWhenLrclibFails() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new IllegalStateException("lrclib down"))
                .thenReturn(ResponseEntity.ok(Map.of("lyrics", "Plain lyrics")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenThrow(new IllegalStateException("search down"));

        ResponseEntity<?> response = controller.getLyrics(-1L, "", "Song", "Artist", 180, "ytmusic");

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("lyrics_ovh", body.get("source"));
        assertEquals("Plain lyrics", body.get("plain"));
    }

    @Test
    void bodyMapperReturnsEmptyMapWhenLyricsAreMissing() {
        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(controller, "mapLrclibBody", (Object) null));
        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(
                controller, "mapLrclibBody", Map.of("plainLyrics", "")));
    }

    @Test
    void refreshArtistImageNormalizesFilenameAndToleratesProviderFailure() {
        ReflectionTestUtils.invokeMethod(controller, "refreshArtistImage", "David_Guetta.jpg");
        verify(musicService).lookupArtistImage("David Guetta");

        doThrow(new IllegalStateException("provider down"))
                .when(musicService).lookupArtistImage("Aitana");
        ReflectionTestUtils.invokeMethod(controller, "refreshArtistImage", "Aitana.png");
        ReflectionTestUtils.invokeMethod(controller, "refreshArtistImage", ".jpg");
    }

    @Test
    void missingArtistImageIsRegeneratedBeforeServing() {
        when(musicService.getArtistAutoImageDir()).thenReturn(tempDir);
        when(musicService.lookupArtistImage("missing artist")).thenAnswer(invocation -> {
            Files.writeString(tempDir.resolve("missing_artist.jpg"), "image");
            return Map.of("found", true);
        });

        ResponseEntity<?> response = controller.artistAutoImage("missing_artist.jpg");

        assertEquals(200, response.getStatusCode().value());
        verify(musicService).lookupArtistImage("missing artist");
    }

    @Test
    void lrclibSearchHandlesEmptyAndUnusableResults() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenReturn(ResponseEntity.ok(new Map[0]));

        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(
                controller, "fetchLrclibLyrics", "Song", "Artist", 180));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenReturn(ResponseEntity.ok(new Map[]{Map.of("trackName", "Song")}));
        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(
                controller, "fetchLrclibLyrics", "Song", "Artist", 180));
    }

    @Test
    void lyricsOvhFailureReturnsEmptyResult() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new IllegalStateException("provider down"));

        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(
                controller, "fetchLyricsOvh", "Song", "Artist"));
    }

    @Test
    void lyricProviderFallbackBranchesHandleBlankAndNullPayloads() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new IllegalStateException("direct lookup down"));
        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(
                controller, "fetchLrclibLyrics", " ", null, 0));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenReturn(ResponseEntity.ok(null));
        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(
                controller, "fetchLrclibLyrics", "Song", null, 0));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(null))
                .thenReturn(ResponseEntity.ok(Map.of("lyrics", "")))
                .thenReturn(ResponseEntity.ok(Map.of("lyrics", 42)));
        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(controller, "fetchLyricsOvh", "Song", "Artist"));
        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(controller, "fetchLyricsOvh", "Song", "Artist"));
        assertEquals(Map.of(), ReflectionTestUtils.invokeMethod(controller, "fetchLyricsOvh", "Song", "Artist"));
    }

    @Test
    void getLyricsContinuesWhenLrclibReturnsNoLyrics() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map[].class)))
                .thenReturn(ResponseEntity.ok(new Map[0]));

        ResponseEntity<?> response = controller.getLyrics(-1L, "", "Song", null, 0, "ytmusic");

        assertEquals(Map.of("source", "none"), response.getBody());
    }
}
