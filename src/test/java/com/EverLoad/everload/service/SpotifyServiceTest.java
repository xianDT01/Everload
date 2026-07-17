package com.EverLoad.everload.service;

import com.EverLoad.everload.config.AdminConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpotifyServiceTest {

    private RestTemplate restTemplate;
    private AdminConfigService configService;
    private SpotifyService service;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        configService = mock(AdminConfigService.class);
        service = new SpotifyService(restTemplate, configService);
        when(configService.getApiKey()).thenReturn("key");
    }

    @Test
    void youtubeSearchReturnsNullForEmptyProviderResponse() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(null);

        String result = ReflectionTestUtils.invokeMethod(service, "searchYouTube", "Artist - Song");

        assertNull(result);
    }

    @Test
    void youtubeSearchReturnsFirstVideoResult() {
        Map<String, Object> video = Map.of(
                "id", Map.of("kind", "youtube#video", "videoId", "abc123")
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("items", List.of(video)));

        String result = ReflectionTestUtils.invokeMethod(service, "searchYouTube", "Artist - Song");

        assertEquals("https://www.youtube.com/watch?v=abc123", result);
    }
}
