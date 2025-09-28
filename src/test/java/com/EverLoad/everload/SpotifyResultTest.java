package com.EverLoad.everload;

import com.EverLoad.everload.model.SpotifyResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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
    @Test
    void constructor_asignaValoresCorrectamente() {
        SpotifyResult result = new SpotifyResult("Song A - Artist", "https://youtube.com/watch?v=12345");

        assertThat(result.getTitle()).isEqualTo("Song A - Artist");
        assertThat(result.getYoutubeUrl()).isEqualTo("https://youtube.com/watch?v=12345");
    }

    @Test
    void getters_retornaValoresEsperados() {
        String title = "Track B - Artist";
        String url = "https://youtube.com/watch?v=abcde";

        SpotifyResult result = new SpotifyResult(title, url);

        assertThat(result.getTitle()).isEqualTo(title);
        assertThat(result.getYoutubeUrl()).isEqualTo(url);
    }

}
