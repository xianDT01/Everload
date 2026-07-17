package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PublicConfigController {

    private final AdminConfigService configService;

    @GetMapping("/api/public/auth-hero-images")
    @SuppressWarnings("java:S6863") // A valid default configuration is returned when the optional file cannot be read.
    public ResponseEntity<Map<String, List<String>>> getAuthHeroImages() {
        try {
            String raw = configService.getConfig()
                    .getOrDefault("authHeroImages", AdminConfigService.DEFAULT_AUTH_HERO_IMAGES);
            List<String> images = Arrays.stream(raw.split("[\\r\\n,]+"))
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .filter(url -> url.startsWith("/") || url.startsWith("http://") || url.startsWith("https://"))
                    .limit(12)
                    .toList();
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            log.warn("Could not read configured authentication hero images; using defaults", e);
            List<String> fallback = Arrays.stream(AdminConfigService.DEFAULT_AUTH_HERO_IMAGES.split("\\R"))
                    .toList();
            return ResponseEntity.ok(Map.of("images", fallback));
        }
    }
}
