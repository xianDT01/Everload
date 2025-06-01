package com.EverLoad.everload;

import com.EverLoad.everload.controller.DownloadController;
import com.EverLoad.everload.service.DownloadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DownloadControllerTest {

    private MockMvc mockMvc;
    private DownloadService downloadService;

    @BeforeEach
    void setUp() {
        downloadService = Mockito.mock(DownloadService.class);
        DownloadController controller = new DownloadController(downloadService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testDownloadVideo_OK() throws Exception {
        File tempFile = File.createTempFile("video", ".mp4");
        FileSystemResource resource = new FileSystemResource(tempFile);
        Mockito.when(downloadService.downloadVideo(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(resource));

        mockMvc.perform(get("/api/downloadVideo")
                        .param("videoId", "abc123")
                        .param("resolution", "720"))
                .andExpect(status().isOk());

        tempFile.delete();
    }

    @Test
    void testDownloadMusic_OK() throws Exception {
        File tempFile = File.createTempFile("audio", ".mp3");
        FileSystemResource resource = new FileSystemResource(tempFile);
        Mockito.when(downloadService.downloadMusic(anyString(), anyString()))
                .thenReturn(ResponseEntity.ok(resource));

        mockMvc.perform(get("/api/downloadMusic")
                        .param("videoId", "abc123")
                        .param("format", "mp3"))
                .andExpect(status().isOk());

        tempFile.delete();
    }
}