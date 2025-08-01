package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Tag(name = "YouTube", description = "Búsquedas en YouTube")
@RestController
@RequestMapping("/api/youtube")
@CrossOrigin(origins = "http://localhost:4200")
public class YouTubeController {

    private final RestTemplate restTemplate;
    private final AdminConfigService configService;

    public YouTubeController(RestTemplate restTemplate, AdminConfigService configService) {
        this.restTemplate = restTemplate;
        this.configService = configService;
    }

    @GetMapping("/search")
    public ResponseEntity<String> searchVideos(@RequestParam String query) {
        try {
            String apiKey = configService.getApiKey();
            String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=10&q="
                    + query + "&key=" + apiKey;

            String response = restTemplate.getForObject(url, String.class);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("❌ Error al leer la API key de configuración");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("❌ Error al buscar en YouTube: " + e.getMessage());
        }
    }
}
