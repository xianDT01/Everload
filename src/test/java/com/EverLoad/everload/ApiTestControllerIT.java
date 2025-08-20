package com.EverLoad.everload;

import com.EverLoad.everload.config.AdminConfigService;
import com.EverLoad.everload.controller.ApiTestController;
import com.EverLoad.everload.service.SpotifyService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiTestControllerTest.TestConfig.class)
class ApiTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminConfigService configService;

    @Autowired
    private SpotifyService spotifyService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        AdminConfigService adminConfigService() {
            return Mockito.mock(AdminConfigService.class);
        }

        @Bean
        SpotifyService spotifyService() {
            return Mockito.mock(SpotifyService.class);
        }

        // Declarar explícitamente el controller (opcional si es detectado por component-scan)
        @Bean
        ApiTestController apiTestController(AdminConfigService configService, SpotifyService spotifyService) {
            return new ApiTestController(configService, spotifyService);
        }
    }

    // -------------------- SPOTIFY --------------------

//    @Test
//    void spotify_ok() throws Exception {
//       mockMvc.perform(get("/api/admin/test-api/spotify"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.platform").value("Spotify"))
//                .andExpect(jsonPath("$.status").value("ok"));
//    }

    @Test
    void spotify_error_en_token() throws Exception {
        doThrow(new RuntimeException("no token")).when(spotifyService).getAccessToken();

        mockMvc.perform(get("/api/admin/test-api/spotify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platform").value("Spotify"))
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("no token"));
    }

    // -------------------- YOUTUBE --------------------

    @Test
    void youtube_ok() throws Exception {
        when(configService.getApiKey()).thenReturn("fake-key");

        try (MockedConstruction<RestTemplate> ignored = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, ctx) -> when(mock.getForEntity(anyString(), eq(String.class)))
                        .thenReturn(ResponseEntity.ok("{\"ok\":true}"))
        )) {
            mockMvc.perform(get("/api/admin/test-api/youtube"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.platform").value("YouTube"))
                    .andExpect(jsonPath("$.status").value("ok"));
        }
    }

    @Test
    void youtube_status_500() throws Exception {
        when(configService.getApiKey()).thenReturn("fake-key");

        try (MockedConstruction<RestTemplate> ignored = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, ctx) -> when(mock.getForEntity(anyString(), eq(String.class)))
                        .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"))
        )) {
            mockMvc.perform(get("/api/admin/test-api/youtube"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.platform").value("YouTube"))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Test
    void youtube_exception() throws Exception {
        when(configService.getApiKey()).thenReturn("fake-key");

        try (MockedConstruction<RestTemplate> ignored = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, ctx) -> when(mock.getForEntity(anyString(), eq(String.class)))
                        .thenThrow(new RuntimeException("network down"))
        )) {
            mockMvc.perform(get("/api/admin/test-api/youtube"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.platform").value("YouTube"))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("network down"));
        }
    }

    // -------------------- TIKTOK --------------------

    @Test
    void tiktok_ok() throws Exception {
        try (MockedConstruction<RestTemplate> ignored = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, ctx) -> when(mock.getForEntity(contains("tiktok.com"), eq(String.class)))
                        .thenReturn(ResponseEntity.ok("OK"))
        )) {
            mockMvc.perform(get("/api/admin/test-api/tiktok"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.platform").value("TikTok"))
                    .andExpect(jsonPath("$.status").value("ok"));
        }
    }

    @Test
    void tiktok_exception() throws Exception {
        try (MockedConstruction<RestTemplate> ignored = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, ctx) -> when(mock.getForEntity(anyString(), eq(String.class)))
                        .thenThrow(new RuntimeException("tiktok unreachable"))
        )) {
            mockMvc.perform(get("/api/admin/test-api/tiktok"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.platform").value("TikTok"))
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.message").value("tiktok unreachable"));
        }
    }

    // -------------------- FACEBOOK --------------------

    @Test
    void facebook_ok() throws Exception {
        try (MockedConstruction<RestTemplate> ignored = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, ctx) -> when(mock.getForEntity(contains("facebook.com"), eq(String.class)))
                        .thenReturn(ResponseEntity.ok("OK"))
        )) {
            mockMvc.perform(get("/api/admin/test-api/facebook"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.platform").value("Facebook"))
                    .andExpect(jsonPath("$.status").value("ok"));
        }
    }

    // -------------------- INSTAGRAM --------------------

    @Test
    void instagram_ok() throws Exception {
        try (MockedConstruction<RestTemplate> ignored = Mockito.mockConstruction(
                RestTemplate.class,
                (mock, ctx) -> when(mock.getForEntity(contains("instagram.com"), eq(String.class)))
                        .thenReturn(ResponseEntity.ok("OK"))
        )) {
            mockMvc.perform(get("/api/admin/test-api/instagram"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.platform").value("Instagram"))
                    .andExpect(jsonPath("$.status").value("ok"));
        }
    }
}
