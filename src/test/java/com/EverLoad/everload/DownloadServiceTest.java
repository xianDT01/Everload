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
}
