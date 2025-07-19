package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.SpotifyResult;
import com.EverLoad.everload.service.SpotifyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Spotify", description = "Importar canciones desde Spotify")
@RestController
@RequestMapping("/api/spotify")
@CrossOrigin(origins = "http://localhost:4200")
public class SpotifyController {

    private final SpotifyService spotifyService;

    public SpotifyController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    @PostMapping("/playlist")
    public ResponseEntity<List<SpotifyResult>> getPlaylistSongs(@RequestBody Map<String, String> body) {
        String url = body.get("url");
        String playlistId = spotifyService.extractPlaylistId(url);
        List<SpotifyResult> resultados = spotifyService.getPlaylistTracks(playlistId);
        return ResponseEntity.ok(resultados);
    }
}