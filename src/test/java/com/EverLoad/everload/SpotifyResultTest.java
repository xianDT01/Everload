package com.EverLoad.everload;

import com.EverLoad.everload.model.SpotifyResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpotifyResultTest {
    @Test
    void testSpotifyResultCreation() {
        String title = "Tírame una foto";
        String youtubeUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ";

        SpotifyResult result = new SpotifyResult(title, youtubeUrl);

        assertEquals(title, result.getTitle());
        assertEquals(youtubeUrl, result.getYoutubeUrl());
    }
}
