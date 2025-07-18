package com.EverLoad.everload;

import com.EverLoad.everload.service.DownloadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DownloadServiceTest {
    @Test
    void testDownloadVideo_returnsError_withInvalidId() {
        DownloadService downloadService = new DownloadService();
        ResponseEntity<?> response = downloadService.downloadVideo("invalid_id_123", "720");

        assertEquals(500, response.getStatusCodeValue());
    }
    @Test
    void testDownloadInstagramVideo_returnsError_withInvalidUrl() {
        DownloadService downloadService = new DownloadService();
        // Una URL inválida para probocar un error
        String invalidUrl = "https://www.instagram.com/p/invalid123";
        ResponseEntity<?> response = downloadService.downloadInstagramVideo(invalidUrl);
        // Se probocará un error 500 porque la descarga fallará
        assertEquals(500, response.getStatusCodeValue());
    }
    @Test
    void ta_withInvalidUrl() {
        DownloadService downloadService = new DownloadService();
        String invalidUrl = "https://twitter.com/someuser/status/fakeid123";
        ResponseEntity<?> response = downloadService.downloadTwitterVideo(invalidUrl);
        assertEquals(500, response.getStatusCodeValue());
    }
    @Test
    void testDownloadMusic_returnsError_withInvalidVideoId() {
        DownloadService downloadService = new DownloadService();
        ResponseEntity<?> response = downloadService.downloadMusic("fake_id", "mp3");

        assertEquals(500, response.getStatusCodeValue());
    }
    @Test
    void testGetPlaylistVideos_returnsError_withInvalidUrl() {
        DownloadService downloadService = new DownloadService();
        String invalidPlaylistUrl = "https://www.youtube.com/playlist?list=INVALID123";
        ResponseEntity<?> response = downloadService.getPlaylistVideos(invalidPlaylistUrl);

        assertEquals(500, response.getStatusCodeValue());
    }
    @Test
         void testDownloadFacebookVideo_returnsError_withInvalidUrl() {
        DownloadService downloadService = new DownloadService();
        String invalidUrl = "https://www.facebook.com/watch?v=fakevideo123";
        ResponseEntity<?> response = downloadService.downloadFacebookVideo(invalidUrl);

        assertEquals(500, response.getStatusCodeValue());
    }
    @Test
    void testDownloadTwitterVideo_returnsError_withNullUrl() {
        DownloadService downloadService = new DownloadService();
        ResponseEntity<?> response = downloadService.downloadTwitterVideo(null);

        assertEquals(500, response.getStatusCodeValue());
    }


}
