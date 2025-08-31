package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.Descarga;
import com.EverLoad.everload.model.SpotifyResult;
import com.EverLoad.everload.service.DownloadService;
import com.EverLoad.everload.service.DownloadHistoryService;
import com.EverLoad.everload.service.SpotifyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Spotify", description = "Importar canciones desde Spotify")
@RestController
@RequestMapping("/api/spotify")
@CrossOrigin(origins = "http://localhost:4200")
public class SpotifyController {

    private final SpotifyService spotifyService;
    private final DownloadService downloadService;
    private final DownloadHistoryService downloadHistoryService;

    public SpotifyController(SpotifyService spotifyService, DownloadService downloadService, DownloadHistoryService downloadHistoryService) {
        this.spotifyService = spotifyService;
        this.downloadService = downloadService;
        this.downloadHistoryService = downloadHistoryService;
    }


    @PostMapping("/playlist")
    public ResponseEntity<List<SpotifyResult>> getPlaylistSongs(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String playlistId = spotifyService.extractPlaylistId(url);
        List<SpotifyResult> resultados = spotifyService.getPlaylistTracks(playlistId);
        return ResponseEntity.ok(resultados);
    }


    @PostMapping("/playlist/download")
    public ResponseEntity<Map<String, Object>> downloadSpotifyPlaylist(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String playlistId = spotifyService.extractPlaylistId(url);
        List<SpotifyResult> resultados = spotifyService.getPlaylistTracks(playlistId);

        List<String> descargadas = new ArrayList<>();
        List<String> noEncontradas = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        for (SpotifyResult result : resultados) {
            String youtubeUrl = result.getYoutubeUrl();

            if (youtubeUrl == null || youtubeUrl.isBlank()) {
                noEncontradas.add(result.getTitle());
                continue;
            }

            try {
                String videoId = youtubeUrl.split("v=")[1];
                ResponseEntity<FileSystemResource> resp = downloadService.downloadMusic(videoId, "mp3");

                if (resp.getStatusCode().is2xxSuccessful()) {
                    downloadHistoryService.registrarDescarga(
                            new Descarga(result.getTitle(), "música", "Spotify")
                    );
                    descargadas.add(result.getTitle());
            } else {
                    errores.add(result.getTitle());
                }
            } catch (Exception e) {
                errores.add(result.getTitle());
                e.printStackTrace();
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("descargadas", descargadas);
        response.put("noEncontradas", noEncontradas);
        response.put("errores", errores);
        response.put("mensaje", String.format(
                "✅ %d canciones descargadas. ❌ %d no se encontraron en YouTube. ⚠️ %d errores.",
                descargadas.size(), noEncontradas.size(), errores.size()
        ));

        return ResponseEntity.ok(response);
    }
    
}