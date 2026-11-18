package com.everload.everload.controller;

import com.everload.everload.config.AdminConfigService;
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
            List<String> images = raw.isBlank() ? List.of() : Arrays.stream(raw.split("\\r?\\n|\\r|,", -1))
                    .map(String::trim)
                    .map(url -> url.startsWith("/") || url.startsWith("http://") || url.startsWith("https://") ? url : "")
                    .limit(12)
                    .toList();
            return ResponseEntity.ok(Map.of("images", images));
        } catch (Exception e) {
            log.warn("Could not read configured authentication hero images; using defaults", e);
            return ResponseEntity.ok(Map.of("images", List.of()));
        }
    }
}
