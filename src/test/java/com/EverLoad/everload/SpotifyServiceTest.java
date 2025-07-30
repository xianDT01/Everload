package com.EverLoad.everload;
import com.EverLoad.everload.service.SpotifyService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

public class SpotifyServiceTest {


    @Test
    public void testExtractPlaylistId() {
        SpotifyService service = new SpotifyService(null);
        String url = "https://open.spotify.com/playlist/3VwUtJH3G6Q9TgJ5ivgqJd?si=abc";
        String result = service.extractPlaylistId(url);
        assertEquals("3VwUtJH3G6Q9TgJ5ivgqJd", result);
    }

    @Test
    public void testExtractPlaylistId_InvalidUrl() {
        SpotifyService service = new SpotifyService(null);
        assertThrows(IllegalArgumentException.class, () -> {
            service.extractPlaylistId("https://open.spotify.com/album/1234");
        });
    }


}

