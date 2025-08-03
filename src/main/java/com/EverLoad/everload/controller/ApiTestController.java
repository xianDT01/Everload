package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
import com.EverLoad.everload.service.SpotifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

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
    public ResponseEntity<String> testYouTube() {
        try {
            String apiKey = configService.getApiKey();
            String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=test&type=video&maxResults=1&key=" + apiKey;
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            return response.getStatusCode().is2xxSuccessful()
                    ? ResponseEntity.ok("🟢 YouTube OK")
                    : ResponseEntity.status(500).body("🔴 YouTube ERROR: " + response.getStatusCode());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("🔴 YouTube ERROR: " + e.getMessage());
        }
    }

    @GetMapping("/spotify")
    public ResponseEntity<String> testSpotify() {
        try {
            spotifyService.getAccessToken();
            return ResponseEntity.ok("🟢 Spotify OK");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("🔴 Spotify ERROR: " + e.getMessage());
        }
    }

    @GetMapping("/tiktok")
    public ResponseEntity<String> testTikTok() {
        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.getForEntity("https://www.tiktok.com/", String.class);
            return resp.getStatusCode().is2xxSuccessful()
                    ? ResponseEntity.ok("🟢 TikTok responde correctamente")
                    : ResponseEntity.status(500).body("🔴 TikTok respondió con error");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("🔴 Error al conectar con TikTok: " + e.getMessage());
        }
    }

    @GetMapping("/facebook")
    public ResponseEntity<String> testFacebook() {
        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.getForEntity("https://www.facebook.com/", String.class);
            return resp.getStatusCode().is2xxSuccessful()
                    ? ResponseEntity.ok("🟢 Facebook responde correctamente")
                    : ResponseEntity.status(500).body("🔴 Facebook respondió con error");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("🔴 Error al conectar con Facebook: " + e.getMessage());
        }
    }

    @GetMapping("/instagram")
    public ResponseEntity<String> testInstagram() {
        try {
            RestTemplate rest = new RestTemplate();
            ResponseEntity<String> resp = rest.getForEntity("https://www.instagram.com/", String.class);
            return resp.getStatusCode().is2xxSuccessful()
                    ? ResponseEntity.ok("🟢 Instagram responde correctamente")
                    : ResponseEntity.status(500).body("🔴 Instagram respondió con error");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("🔴 Error al conectar con Instagram: " + e.getMessage());
        }
    }

}
