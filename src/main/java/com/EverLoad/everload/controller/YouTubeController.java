package com.EverLoad.everload.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import com.EverLoad.everload.config.CredentialConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@Tag(name = "YouTube", description = "Búsquedas en YouTube")
@RestController
@RequestMapping("/api/youtube")
@CrossOrigin(origins = "http://localhost:4200")
public class YouTubeController {

    private final RestTemplate restTemplate;
    @Autowired
    private CredentialConfig credentialConfig;

    public YouTubeController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/search")
    public ResponseEntity<String> searchVideos(@RequestParam String query) {
        String key = credentialConfig != null ? credentialConfig.getYoutubeApiKey() : "";
        String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=10&q="
                + query + "&key=" + key;

        String response = restTemplate.getForObject(url, String.class);
        return ResponseEntity.ok(response);
    }
}