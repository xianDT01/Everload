package com.EverLoad.everload;

import com.EverLoad.everload.service.SpotifyService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SpotifyServiceTest {

    @Test
    void extractPlaylistId_supportsWebUrl() {
        SpotifyService svc = new SpotifyService(null, null);
        String id = svc.extractPlaylistId("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc");
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", id);
    }

    @Test
    void extractPlaylistId_invalidUrl_throws() {
        SpotifyService svc = new SpotifyService(null, null);
        assertThrows(IllegalArgumentException.class, () -> svc.extractPlaylistId("https://open.spotify.com/track/xxx"));
    }

}
