package com.everload.everload.controller;

import com.everload.everload.model.Download;
import com.everload.everload.model.SpotifyResult;
import com.everload.everload.service.DownloadService;
import com.everload.everload.service.DownloadHistoryService;
import com.everload.everload.service.SpotifyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Spotify", description = "Importar canciones desde Spotify")
@RestController
@RequestMapping("/api/spotify")
@PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
public class SpotifyController {

    private static final String ERROR_FIELD = "error";

    private final SpotifyService spotifyService;
    private final DownloadService downloadService;
    private final DownloadHistoryService downloadHistoryService;

    public SpotifyController(SpotifyService spotifyService, DownloadService downloadService,
                             DownloadHistoryService downloadHistoryService) {
        this.spotifyService = spotifyService;
        this.downloadService = downloadService;
        this.downloadHistoryService = downloadHistoryService;
    }

    @PostMapping("/playlist")
    public ResponseEntity<Object> getPlaylistSongs(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_FIELD, "URL de playlist requerida"));
        }
        try {
            String playlistId = spotifyService.extractPlaylistId(url);
            List<SpotifyResult> resultados = spotifyService.getPlaylistTracks(playlistId);
            return ResponseEntity.ok(resultados);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_FIELD, e.getMessage()));
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Error al conectar con Spotify";
            return ResponseEntity.status(502).body(Map.of(ERROR_FIELD, msg));
        }
    }

    @PostMapping("/playlist/download")
    public ResponseEntity<Map<String, Object>> downloadSpotifyPlaylist(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_FIELD, "URL de playlist requerida"));
        }

        List<String> descargadas = new ArrayList<>();
        List<String> noEncontradas = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        try {
            String playlistId = spotifyService.extractPlaylistId(url);
            List<SpotifyResult> resultados = spotifyService.getPlaylistTracks(playlistId);

            for (SpotifyResult result : resultados) {
                String youtubeUrl = result.getYoutubeUrl();
                if (youtubeUrl == null || youtubeUrl.isBlank()) {
                    noEncontradas.add(result.getTitle());
                    continue;
                }
                downloadSpotifyTrack(result, youtubeUrl, descargadas, errores);
            }
        } catch (Exception e) {
            errores.add("Error general: " + e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("descargadas", descargadas);
        response.put("noEncontradas", noEncontradas);
        response.put("errores", errores);
        response.put("mensaje", String.format(
                "✅ %d canciones descargadas. ❌ %d no se encontraron en YouTube. ⚠️ %d errores.",
                descargadas.size(), noEncontradas.size(), errores.size()));
        return ResponseEntity.ok(response);
    }

    private void downloadSpotifyTrack(SpotifyResult result, String youtubeUrl,
                                      List<String> downloaded, List<String> errors) {
        try {
            String videoId = youtubeUrl.split("v=")[1];
            ResponseEntity<FileSystemResource> response = downloadService.downloadMusic(videoId, "mp3");
            if (!response.getStatusCode().is2xxSuccessful()) {
                errors.add(result.getTitle());
                return;
            }
            downloadHistoryService.recordDownload(new Download(result.getTitle(), "música", "Spotify"));
            downloaded.add(result.getTitle());
        } catch (Exception e) {
            errors.add(result.getTitle());
        }
    }
}
