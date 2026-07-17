package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
import com.EverLoad.everload.service.SpotifyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiTestControllerTest {

    private AdminConfigService configService;
    private SpotifyService spotifyService;
    private RestTemplate restTemplate;
    private ApiTestController controller;

    @BeforeEach
    void setUp() {
        configService = mock(AdminConfigService.class);
        spotifyService = mock(SpotifyService.class);
        restTemplate = mock(RestTemplate.class);
        controller = new ApiTestController(configService, spotifyService, restTemplate);
    }

    @Test
    void youtubeReportsSuccessfulConnection() throws IOException {
        when(configService.getApiKey()).thenReturn("key");
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        Map<String, String> result = controller.testYouTube().getBody();

        assertEquals("YouTube", result.get("platform"));
        assertEquals("ok", result.get("status"));
    }

    @Test
    void youtubeAndSpotifyExposeProviderErrors() throws Exception {
        when(configService.getApiKey()).thenThrow(new IOException("config unavailable"));
        doThrow(new IllegalStateException("spotify unavailable")).when(spotifyService).testConnection();

        Map<String, String> youtube = controller.testYouTube().getBody();
        Map<String, String> spotify = controller.testSpotify().getBody();

        assertEquals("error", youtube.get("status"));
        assertEquals("config unavailable", youtube.get("message"));
        assertEquals("error", spotify.get("status"));
        assertEquals("spotify unavailable", spotify.get("message"));
    }

    @Test
    void socialEndpointsReportNonSuccessfulStatus() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("down"));

        Map<String, String> tiktok = controller.testTikTok().getBody();
        Map<String, String> facebook = controller.testFacebook().getBody();
        Map<String, String> instagram = controller.testInstagram().getBody();

        assertEquals("error", tiktok.get("status"));
        assertEquals("Código de estado: 503 SERVICE_UNAVAILABLE", tiktok.get("message"));
        assertEquals("error", facebook.get("status"));
        assertEquals("error", instagram.get("status"));
    }

    @Test
    void youtubeReportsNonSuccessfulHttpStatus() throws IOException {
        when(configService.getApiKey()).thenReturn("key");
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("denied"));

        Map<String, String> result = controller.testYouTube().getBody();

        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").endsWith("401 UNAUTHORIZED"));
    }

    @Test
    void socialEndpointsExposeTransportErrors() {
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new IllegalStateException("network unavailable"));

        Map<String, String> tiktok = controller.testTikTok().getBody();
        Map<String, String> facebook = controller.testFacebook().getBody();
        Map<String, String> instagram = controller.testInstagram().getBody();

        assertEquals("network unavailable", tiktok.get("message"));
        assertEquals("network unavailable", facebook.get("message"));
        assertEquals("network unavailable", instagram.get("message"));
    }
}
