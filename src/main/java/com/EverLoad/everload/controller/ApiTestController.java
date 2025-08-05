package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
import com.EverLoad.everload.service.SpotifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/test-api")
public class ApiTestController {

    private final AdminConfigService configService;
    private final SpotifyService spotifyService;

    public ApiTestController(AdminConfigService configService, SpotifyService spotifyService) {
        this.configService = configService;
        this.spotifyService = spotifyService;
    }

    @GetMapping("/youtube")
    public ResponseEntity<Map<String, String>> testYouTube() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "YouTube");

        try {
            String apiKey = configService.getApiKey();
            String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=test&type=video&maxResults=1&key=" + apiKey;
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> apiResponse = restTemplate.getForEntity(url, String.class);

            if (apiResponse.getStatusCode().is2xxSuccessful()) {
                response.put("status", "ok");
            } else {
                response.put("status", "error");
                response.put("message", "Código de estado: " + apiResponse.getStatusCode());
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/spotify")
    public ResponseEntity<Map<String, String>> testSpotify() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "Spotify");

        try {
            spotifyService.getAccessToken();
            response.put("status", "ok");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/tiktok")
    public ResponseEntity<Map<String, String>> testTikTok() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "TikTok");

        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.getForEntity("https://www.tiktok.com/", String.class);

            response.put("status", resp.getStatusCode().is2xxSuccessful() ? "ok" : "error");
            if (!resp.getStatusCode().is2xxSuccessful()) {
                response.put("message", "Código de estado: " + resp.getStatusCode());
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/facebook")
    public ResponseEntity<Map<String, String>> testFacebook() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "Facebook");

        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.getForEntity("https://www.facebook.com/", String.class);

            response.put("status", resp.getStatusCode().is2xxSuccessful() ? "ok" : "error");
            if (!resp.getStatusCode().is2xxSuccessful()) {
                response.put("message", "Código de estado: " + resp.getStatusCode());
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/instagram")
    public ResponseEntity<Map<String, String>> testInstagram() {
        Map<String, String> response = new HashMap<>();
        response.put("platform", "Instagram");

        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.getForEntity("https://www.instagram.com/", String.class);

            response.put("status", resp.getStatusCode().is2xxSuccessful() ? "ok" : "error");
            if (!resp.getStatusCode().is2xxSuccessful()) {
                response.put("message", "Código de estado: " + resp.getStatusCode());
            }

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


}
