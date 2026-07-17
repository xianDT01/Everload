package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
import com.EverLoad.everload.service.SpotifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/test-api")
@PreAuthorize("hasRole('ADMIN')")
public class ApiTestController {

    private static final String STATUS_CODE_PREFIX = "Código de estado: ";
    private static final String MESSAGE_FIELD = "message";

    private final AdminConfigService configService;
    private final SpotifyService spotifyService;
    private final RestTemplate restTemplate;

    public ApiTestController(AdminConfigService configService, SpotifyService spotifyService,
                             RestTemplate restTemplate) {
        this.configService = configService;
        this.spotifyService = spotifyService;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/youtube")
    public ResponseEntity<Map<String, String>> testYouTube() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "YouTube");

        try {
            String apiKey = configService.getApiKey();
            String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=test&type=video&maxResults=1&key=" + apiKey;
            ResponseEntity<String> apiResponse = restTemplate.getForEntity(url, String.class);

            if (apiResponse.getStatusCode().is2xxSuccessful()) {
                response.put("status", "ok");
            } else {
                response.put("status", "error");
                response.put(MESSAGE_FIELD, STATUS_CODE_PREFIX + apiResponse.getStatusCode());
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put(MESSAGE_FIELD, e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/spotify")
    public ResponseEntity<Map<String, String>> testSpotify() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "Spotify");

        try {
            spotifyService.testConnection();
            response.put("status", "ok");
        } catch (Exception e) {
            response.put("status", "error");
            response.put(MESSAGE_FIELD, e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/tiktok")
    public ResponseEntity<Map<String, String>> testTikTok() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "TikTok");

        try {
            ResponseEntity<String> resp = restTemplate.getForEntity("https://www.tiktok.com/", String.class);

            response.put("status", resp.getStatusCode().is2xxSuccessful() ? "ok" : "error");
            if (!resp.getStatusCode().is2xxSuccessful()) {
                response.put(MESSAGE_FIELD, STATUS_CODE_PREFIX + resp.getStatusCode());
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put(MESSAGE_FIELD, e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/facebook")
    public ResponseEntity<Map<String, String>> testFacebook() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "Facebook");

        try {
            ResponseEntity<String> resp = restTemplate.getForEntity("https://www.facebook.com/", String.class);

            response.put("status", resp.getStatusCode().is2xxSuccessful() ? "ok" : "error");
            if (!resp.getStatusCode().is2xxSuccessful()) {
                response.put(MESSAGE_FIELD, STATUS_CODE_PREFIX + resp.getStatusCode());
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put(MESSAGE_FIELD, e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/instagram")
    public ResponseEntity<Map<String, String>> testInstagram() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "Instagram");

        try {
            ResponseEntity<String> resp = restTemplate.getForEntity("https://www.instagram.com/", String.class);

            response.put("status", resp.getStatusCode().is2xxSuccessful() ? "ok" : "error");
            if (!resp.getStatusCode().is2xxSuccessful()) {
                response.put(MESSAGE_FIELD, STATUS_CODE_PREFIX + resp.getStatusCode());
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put(MESSAGE_FIELD, e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


}
