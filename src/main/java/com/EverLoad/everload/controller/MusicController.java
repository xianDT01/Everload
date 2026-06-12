package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.MusicMetadataDto;
import com.EverLoad.everload.dto.PagedMusicResult;
import com.EverLoad.everload.service.MusicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import org.springframework.core.io.FileSystemResource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Tag(name = "Music Center", description = "Explorador musical y streaming de audio desde NAS")
@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Operation(summary = "Canciones aleatorias con portada para el panel de inicio")
    @GetMapping("/random")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getRandomTracks(@RequestParam(defaultValue = "3") int count) {
        try {
            return ResponseEntity.ok(musicService.getRandomTracks(Math.min(count, 10)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Buscar archivos de audio recursivamente por nombre, título, artista o álbum")
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> searchMusic(@RequestParam Long pathId,
                                         @RequestParam String query,
                                         @RequestParam(required = false) String subPath,
                                         @RequestParam(defaultValue = "200") int limit) {
        try {
            return ResponseEntity.ok(musicService.searchMusic(pathId, subPath, query, Math.min(limit, 500)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Resumen cacheado de la biblioteca musical")
    @GetMapping("/library-overview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> libraryOverview(@RequestParam Long pathId,
                                             @RequestParam(defaultValue = "5000") int limit) {
        try {
            return ResponseEntity.ok(musicService.getLibraryOverview(pathId, Math.min(limit, 10000)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Canciones añadidas recientemente desde la cache musical")
    @GetMapping("/recent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> recentMusic(@RequestParam Long pathId,
                                         @RequestParam(defaultValue = "40") int limit) {
        try {
            return ResponseEntity.ok(musicService.getRecentTracks(pathId, Math.min(limit, 200)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Iniciar indexado cacheado de la biblioteca musical")
    @PostMapping("/library-index")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> startLibraryIndex(@RequestParam Long pathId) {
        try {
            return ResponseEntity.ok(musicService.startLibraryIndex(pathId));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Canciones cacheadas de un artista")
    @GetMapping("/artist-tracks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> artistTracks(@RequestParam Long pathId,
                                          @RequestParam String artist,
                                          @RequestParam(required = false) List<String> aliases,
                                          @RequestParam(defaultValue = "500") int limit) {
        try {
            return ResponseEntity.ok(musicService.getCachedTracksByArtist(pathId, artist, aliases, Math.min(limit, 1000)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Buscar imagen automática de artista")
    @GetMapping("/artist-image")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> artistImage(@RequestParam String artist) {
        if (artist == null || artist.isBlank() || artist.length() > 160) {
            return ResponseEntity.badRequest().body(Map.of("error", "Artista inválido"));
        }
        try {
            return ResponseEntity.ok(musicService.lookupArtistImage(artist));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("found", false));
        }
    }

    @Operation(summary = "Servir imagen de artista guardada automáticamente en servidor")
    @GetMapping("/artist-auto-image/{filename:.+}")
    public ResponseEntity<?> artistAutoImage(@PathVariable String filename) {
        try {
            Path dir = musicService.getArtistAutoImageDir();
            Path file = dir.resolve(filename).normalize();
            if (!file.startsWith(dir) || !Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(file);
            if (contentType == null) contentType = "image/jpeg";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Buscar portada de álbum via MusicBrainz + Cover Art Archive")
    @GetMapping("/album-cover")
    public ResponseEntity<?> albumCover(
            @RequestParam(required = false, defaultValue = "") String artist,
            @RequestParam String album) {
        if (album == null || album.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Se requiere el álbum"));
        }
        return ResponseEntity.ok(musicService.lookupAlbumCover(artist, album));
    }

    @Operation(summary = "Servir portada de álbum guardada automáticamente en servidor")
    @GetMapping("/album-auto-cover/{filename:.+}")
    public ResponseEntity<?> albumAutoCover(@PathVariable String filename) {
        try {
            Path dir = musicService.getAlbumCoverAutoDir();
            Path file = dir.resolve(filename).normalize();
            if (!file.startsWith(dir) || !Files.exists(file)) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(file);
            if (contentType == null) contentType = "image/jpeg";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Listar archivos de audio con metadatos ID3 (paginado)")
    @GetMapping("/metadata")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> browseMusic(@RequestParam Long pathId,
                                         @RequestParam(required = false) String subPath,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size) {
        try {
            PagedMusicResult result = musicService.listFilesWithMetadata(pathId, subPath, page, size);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Actualizar metadatos ID3 de un archivo de audio")
    @PutMapping("/metadata")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> updateMetadata(@RequestBody MetadataUpdateRequest req) {
        try {
            musicService.updateMetadata(req.getPathId(), req.getRelativePath(), req.getTitle(), req.getArtist(), req.getAlbum(), req.getYear());
            return ResponseEntity.ok(Map.of("message", "Metadatos actualizados"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Data
    static class MetadataUpdateRequest {
        private Long pathId;
        private String relativePath;
        private String title;
        private String artist;
        private String album;
        private String year;
    }

    // ── YouTube metadata lookup ───────────────────────────────────────────────

    @Operation(summary = "Buscar metadatos de una canción en YouTube via yt-dlp")
    @GetMapping("/youtube-metadata")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> fetchYoutubeMetadata(@RequestParam String query) {
        if (query == null || query.isBlank() || query.length() > 300) {
            return ResponseEntity.badRequest().body(Map.of("error", "Consulta inválida"));
        }
        try {
            return ResponseEntity.ok(musicService.lookupYoutubeMetadataMap(query));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al buscar en YouTube: " + e.getMessage()));
        }
    }

    @Operation(summary = "Rellenar metadatos masivamente desde YouTube via yt-dlp")
    @PostMapping("/youtube-metadata/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> fillYoutubeMetadataBulk(@RequestParam Long pathId,
                                                     @RequestParam(required = false) String subPath,
                                                     @RequestParam(defaultValue = "50") int limit,
                                                     @RequestParam(defaultValue = "true") boolean onlyMissing) {
        try {
            return ResponseEntity.ok(musicService.fillYoutubeMetadataBulk(pathId, subPath, limit, onlyMissing));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Streaming ─────────────────────────────────────────────────────────────

    /**
     * Serves audio with proper Range support.
     * Written directly to HttpServletResponse to avoid Spring MVC
     * ResourceRegion/message-converter issues across different Spring versions.
     *
     * The JWT token can be passed as ?token= query param because HTMLAudioElement
     * cannot set custom request headers.
     */
    @Operation(summary = "Streaming de audio con soporte Accept-Ranges y calidad configurable")
    @GetMapping("/stream")
    @PreAuthorize("isAuthenticated()")
    public void streamAudio(@RequestParam Long pathId,
                            @RequestParam String subPath,
                            @RequestParam(required = false, defaultValue = "original") String quality,
                            @RequestHeader(value = "Range", required = false) String rangeHeader,
                            HttpServletResponse response) {
        try {
            musicService.streamAudioToResponse(pathId, subPath, rangeHeader, quality, response);
        } catch (SecurityException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // ── Cover art ─────────────────────────────────────────────────────────────

    @Operation(summary = "Preparar cache HLS para audios largos")
    @PostMapping("/hls/prepare")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> prepareHls(@RequestParam Long pathId,
                                        @RequestParam String subPath) {
        try {
            return ResponseEntity.ok(musicService.prepareHlsStream(pathId, subPath));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Estado de cache HLS para audios largos")
    @GetMapping("/hls/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> hlsStatus(@RequestParam Long pathId,
                                       @RequestParam String subPath) {
        try {
            return ResponseEntity.ok(musicService.getHlsStatus(pathId, subPath));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Playlist HLS cacheada para audios largos")
    @GetMapping(value = "/hls/playlist", produces = "application/vnd.apple.mpegurl")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> hlsPlaylist(@RequestParam Long pathId,
                                              @RequestParam String subPath,
                                              HttpServletRequest request) {
        try {
            String playlist = musicService.getHlsPlaylist(pathId, subPath, request.getParameter("token"));
            HttpHeaders headers = new HttpHeaders();
            headers.setCacheControl("private, max-age=30");
            return new ResponseEntity<>(playlist, headers, HttpStatus.OK);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("#EXTM3U\n");
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("");
        }
    }

    @Operation(summary = "Segmento HLS cacheado para audios largos")
    @GetMapping("/hls/segment")
    @PreAuthorize("isAuthenticated()")
    public void hlsSegment(@RequestParam Long pathId,
                           @RequestParam String subPath,
                           @RequestParam String segment,
                           HttpServletResponse response) {
        try {
            musicService.streamHlsSegmentToResponse(pathId, subPath, segment, response);
        } catch (SecurityException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Operation(summary = "Carátula embebida en los tags ID3 del archivo de audio")
    @GetMapping("/cover")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getCoverArt(@RequestParam Long pathId,
                                              @RequestParam String subPath) {
        try {
            byte[] cover = musicService.getCoverArt(pathId, subPath);
            if (cover != null && cover.length > 0) {
                HttpHeaders headers = new HttpHeaders();
                // Detect actual image type from first bytes (JPEG starts with FFD8, PNG with 89504E47)
                if (cover.length > 4 && cover[0] == (byte) 0x89 && cover[1] == 0x50) {
                    headers.setContentType(MediaType.IMAGE_PNG);
                } else {
                    headers.setContentType(MediaType.IMAGE_JPEG);
                }
                headers.setContentLength(cover.length);
                headers.setCacheControl("public, max-age=3600"); // 1h — se refresca tras subir portada
                return new ResponseEntity<>(cover, headers, HttpStatus.OK);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Carátula del primer archivo de audio encontrado en la carpeta")
    @GetMapping("/folder-cover")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getFolderCoverArt(@RequestParam Long pathId,
                                                    @RequestParam(required = false, defaultValue = "") String subPath) {
        try {
            byte[] cover = musicService.getFolderCoverArt(pathId, subPath);
            if (cover != null && cover.length > 0) {
                HttpHeaders headers = new HttpHeaders();
                if (cover.length > 4 && cover[0] == (byte) 0x89 && cover[1] == 0x50) {
                    headers.setContentType(MediaType.IMAGE_PNG);
                } else {
                    headers.setContentType(MediaType.IMAGE_JPEG);
                }
                headers.setContentLength(cover.length);
                headers.setCacheControl("public, max-age=86400");
                return new ResponseEntity<>(cover, headers, HttpStatus.OK);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── YouTube DJ Cache ──────────────────────────────────────────────────────

    @Operation(summary = "Preparar y cachear audio de youtube para la cabina DJ")
    @PostMapping("/youtube/prepare")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> prepareYoutubeTrack(@RequestParam String videoId) {
        try {
            musicService.prepareYoutubeTrack(videoId);
            return ResponseEntity.ok(Map.of("message", "Ready", "videoId", videoId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Streaming de audio cacheado de youtube con soporte Accept-Ranges")
    @GetMapping("/youtube/stream")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public void streamYoutubeAudio(@RequestParam String videoId,
                                   @RequestHeader(value = "Range", required = false) String rangeHeader,
                                   HttpServletResponse response) {
        try {
            musicService.streamYoutubeAudioToResponse(videoId, rangeHeader, response);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    @Operation(summary = "Obtener miniatura de youtube redireccionada")
    @GetMapping("/youtube/cover")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> getYoutubeCover(@RequestParam String videoId) {
        // Redirect to high quality youtube thumbnail
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(java.net.URI.create("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg"));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    // ── Lyrics ────────────────────────────────────────────────────────────────

    @Operation(summary = "Obtener letra/LRC para una pista del NAS")
    @GetMapping("/lyrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> getLyrics(
            @RequestParam Long pathId,
            @RequestParam String subPath,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String artist,
            @RequestParam(required = false, defaultValue = "0") int duration,
            @RequestParam(required = false) String source) {

        // 1. Look for a .lrc sidecar file next to the audio file
        if (pathId != null && pathId >= 0 && !"ytmusic".equalsIgnoreCase(source)) {
            String lrc = musicService.findLrcSidecar(pathId, subPath);
            if (lrc != null) {
                return ResponseEntity.ok(Map.of("source", "file", "lrc", lrc));
            }
        }

        // 2. LRCLIB (synced lyrics preferred, free, no key required).
        if (title != null && !title.isBlank()) {
            Map<String, Object> lyrics = fetchLrclibLyrics(title, artist, duration);
            if (lyrics != null) return ResponseEntity.ok(lyrics);
        }

        // 3. lyrics.ovh (plain text, broader coverage of popular songs).
        if (title != null && !title.isBlank() && artist != null && !artist.isBlank()) {
            Map<String, Object> lyrics = fetchLyricsOvh(title, artist);
            if (lyrics != null) return ResponseEntity.ok(lyrics);
        }

        return ResponseEntity.ok(Map.of("source", "none"));
    }

    private Map<String, Object> fetchLrclibLyrics(String title, String artist, int duration) {
        RestTemplate rt = new RestTemplate();
        HttpHeaders h = new HttpHeaders();
        h.set("User-Agent", "EverLoad/1.0 (https://github.com/everload)");

        try {
            String url = "https://lrclib.net/api/get?track_name=" + enc(cleanLyricsTerm(title)) +
                    (artist != null && !artist.isBlank() ? "&artist_name=" + enc(cleanLyricsTerm(artist)) : "") +
                    (duration > 0 ? "&duration=" + duration : "");
            ResponseEntity<Map> resp = rt.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class);
            Map<String, Object> mapped = mapLrclibBody(resp.getBody());
            if (mapped != null) return mapped;
        } catch (Exception ignored) {}

        try {
            String query = cleanLyricsTerm(title + " " + (artist == null ? "" : artist)).trim();
            if (query.isBlank()) return null;
            ResponseEntity<Map[]> resp = rt.exchange(
                    "https://lrclib.net/api/search?q=" + enc(query),
                    HttpMethod.GET,
                    new HttpEntity<>(h),
                    Map[].class
            );
            Map[] body = resp.getBody();
            if (body == null || body.length == 0) return null;
            return java.util.Arrays.stream(body)
                    .filter(item -> item != null && hasAnyLyrics(item))
                    .min(Comparator.comparingInt(item -> lyricsDistance(item, title, artist, duration)))
                    .map(this::mapLrclibBody)
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> mapLrclibBody(Map body) {
        if (body == null) return null;
        Object syncedLyrics = body.get("syncedLyrics");
        Object plainLyrics = body.get("plainLyrics");
        if (syncedLyrics instanceof String synced && !synced.isBlank()) {
            return Map.of("source", "lrclib", "lrc", synced);
        }
        if (plainLyrics instanceof String plain && !plain.isBlank()) {
            return Map.of("source", "lrclib_plain", "plain", plain);
        }
        return null;
    }

    private boolean hasAnyLyrics(Map item) {
        return item.get("syncedLyrics") instanceof String synced && !synced.isBlank()
                || item.get("plainLyrics") instanceof String plain && !plain.isBlank();
    }

    private int lyricsDistance(Map item, String title, String artist, int duration) {
        String foundTitle = String.valueOf(item.getOrDefault("trackName", ""));
        String foundArtist = String.valueOf(item.getOrDefault("artistName", ""));
        int score = cleanLyricsTerm(foundTitle).equalsIgnoreCase(cleanLyricsTerm(title)) ? 0 : 20;
        if (artist != null && !artist.isBlank()
                && cleanLyricsTerm(foundArtist).toLowerCase().contains(cleanLyricsTerm(artist).toLowerCase())) {
            score -= 10;
        }
        if (duration > 0 && item.get("duration") instanceof Number n) {
            score += Math.min(30, Math.abs(n.intValue() - duration));
        }
        return score;
    }

    private Map<String, Object> fetchLyricsOvh(String title, String artist) {
        try {
            RestTemplate rt = new RestTemplate();
            HttpHeaders h = new HttpHeaders();
            h.set("User-Agent", "EverLoad/1.0 (https://github.com/everload)");
            String url = "https://lyrics.ovh/v1/"
                    + enc(cleanLyricsTerm(artist)).replace("+", "%20")
                    + "/" + enc(cleanLyricsTerm(title)).replace("+", "%20");
            ResponseEntity<Map> resp = rt.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class);
            if (resp.getBody() != null && resp.getBody().get("lyrics") instanceof String lyrics && !lyrics.isBlank()) {
                return Map.of("source", "lyrics_ovh", "plain", lyrics);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String cleanLyricsTerm(String value) {
        return (value == null ? "" : value)
                .replaceAll("(?i)\\s*\\((official\\s*(music\\s*)?video|official\\s*audio|lyric\\s*video|visualizer|remaster(ed)?|audio|video|explicit)\\)", "")
                .replaceAll("(?i)\\s*-\\s*(official\\s*(music\\s*)?video|official\\s*audio|lyric\\s*video|visualizer|remaster(ed)?|audio|video).*", "")
                .replaceAll("(?i)\\s*(\\(|\\[)\\s*(feat|ft)\\.?\\s+[^)\\]]+[)\\]]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
