package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.YouTubeSearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "YouTube", description = "Búsquedas en YouTube")
@RestController
@RequestMapping("/api/youtube")
@PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
@RequiredArgsConstructor
public class YouTubeController {

    private static final String ERROR_KEY = "error";

    private final YouTubeSearchService youTubeSearchService;

    @GetMapping("/search")
    public ResponseEntity<Object> searchVideos(@RequestParam String query,
                                               @RequestParam(defaultValue = "10") int maxResults) {
        if (query == null || query.isBlank() || query.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Consulta inválida"));
        }
        maxResults = Math.max(1, Math.min(maxResults, 50)); // clamp 1–50
        try {
            return ResponseEntity.ok(Map.of("items", youTubeSearchService.search(query, maxResults)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().body(Map.of(ERROR_KEY, "Búsqueda interrumpida"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of(ERROR_KEY, "Error al buscar en YouTube: " + e.getMessage()));
        }
    }
}
