package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.DownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @Operation(summary = "Descargar un vídeo")
    @GetMapping("/downloadVideo")
    public ResponseEntity<FileSystemResource> downloadVideo(
            @RequestParam String videoId,
            @RequestParam String resolution) {
        return downloadService.downloadVideo(videoId, resolution);
    }

    @Operation(summary = "Descargar música")
    @GetMapping("/downloadMusic")
    public ResponseEntity<FileSystemResource> downloadMusic(
            @RequestParam String videoId,
            @RequestParam String format) {
        return downloadService.downloadMusic(videoId, format);
    }


    @RestController
    @RequestMapping("/api/youtube")
    @CrossOrigin(origins = "http://localhost:4200")
    public class YouTubeController {

        private final RestTemplate restTemplate;
        private final String API_KEY = "";

        public YouTubeController(RestTemplate restTemplate) {
            this.restTemplate = restTemplate;
        }

        @GetMapping("/search")
        public ResponseEntity<String> searchVideos(@RequestParam String query) {
            String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=10&q="
                    + query + "&key=" + API_KEY;

            String response = restTemplate.getForObject(url, String.class);
            return ResponseEntity.ok(response);
        }
    }
}
