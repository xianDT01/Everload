package com.EverLoad.everload.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "YouTube", description = "Búsquedas en YouTube")
@RestController
@RequestMapping("/api/youtube")
@PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
public class YouTubeController {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Searches YouTube using yt-dlp (ytsearch), bypassing the YouTube Data API quota.
     * Returns a structure compatible with the YouTube Data API v3 response so the
     * frontend doesn't need any changes.
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchVideos(@RequestParam String query,
                                          @RequestParam(defaultValue = "10") int maxResults) {
        try {
            // yt-dlp --flat-playlist prints one JSON object per result
            String ytSearch = "ytsearch" + maxResults + ":" + query;
            ProcessBuilder pb = new ProcessBuilder(
                    "yt-dlp",
                    "--flat-playlist",
                    "--print", "%(id)s\t%(title)s\t%(uploader)s\t%(duration)s\t%(thumbnails.0.url)s",
                    "--no-warnings",
                    ytSearch
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            List<Map<String, Object>> items = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t", 5);
                    if (parts.length < 2) continue;
                    String id       = parts[0].trim();
                    String title    = parts[1].trim();
                    String uploader = parts.length > 2 ? parts[2].trim() : "";
                    String thumbUrl = parts.length > 4 ? parts[4].trim() : "https://img.youtube.com/vi/" + id + "/mqdefault.jpg";

                    // Build a response shape compatible with YouTube Data API v3
                    items.add(Map.of(
                        "id", Map.of("videoId", id),
                        "snippet", Map.of(
                            "title", title,
                            "channelTitle", uploader,
                            "thumbnails", Map.of(
                                "default", Map.of("url", "https://img.youtube.com/vi/" + id + "/default.jpg"),
                                "high",    Map.of("url", "https://img.youtube.com/vi/" + id + "/hqdefault.jpg")
                            )
                        )
                    ));
                }
            }
            process.waitFor();

            return ResponseEntity.ok(Map.of("items", items));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al buscar en YouTube: " + e.getMessage()));
        }
    }
}
