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
            // Clean common file-extension noise from the query
            String cleanQuery = query
                    .replaceAll("\\.(mp3|flac|m4a|wav|ogg|aac|opus|wma|alac)$", "")
                    .replaceAll("[_\\[\\]{}()]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            ProcessBuilder pb = new ProcessBuilder(
                    "yt-dlp",
                    "--flat-playlist",
                    "--print", "%(title)s\t%(uploader)s\t%(id)s",
                    "--no-warnings",
                    "ytsearch1:" + cleanQuery
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String resultLine;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                resultLine = reader.readLine();
            }
            // Drain stderr to prevent blocking
            try (java.io.InputStream errStream = process.getErrorStream()) {
                errStream.readAllBytes();
            }
            process.waitFor();

            if (resultLine == null || resultLine.isBlank()) {
                return ResponseEntity.ok(Map.of("found", false));
            }

            String[] parts = resultLine.split("\t", 3);
            String rawTitle = parts[0].trim();
            String channelName = parts.length > 1 ? parts[1].trim() : "";
            String videoId = parts.length > 2 ? parts[2].trim() : "";

            // Heuristic: split "Artist - Title" pattern
            String parsedTitle = rawTitle;
            String parsedArtist = channelName;
            int dashIdx = rawTitle.indexOf(" - ");
            if (dashIdx > 0) {
                parsedArtist = rawTitle.substring(0, dashIdx).trim();
                parsedTitle = rawTitle.substring(dashIdx + 3).trim();
            }

            // Clean common noise from parsed title
            parsedTitle = parsedTitle
                    .replaceAll("(?i)\\s*\\(?(official\\s*(music\\s*)?video|lyric\\s*video|official\\s*audio|audio\\s*oficial|video\\s*oficial|visualizer|hd|hq|4k)\\)?", "")
                    .replaceAll("\\s*[\\[({].*?[\\])}]\\s*$", "")
                    .trim();

            return ResponseEntity.ok(Map.of(
                    "found", true,
                    "title", parsedTitle,
                    "artist", parsedArtist,
                    "videoId", videoId,
                    "channelName", channelName,
                    "rawTitle", rawTitle
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al buscar en YouTube: " + e.getMessage()));
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
    @Operation(summary = "Streaming de audio con soporte Accept-Ranges")
    @GetMapping("/stream")
    @PreAuthorize("isAuthenticated()")
    public void streamAudio(@RequestParam Long pathId,
                            @RequestParam String subPath,
                            @RequestHeader(value = "Range", required = false) String rangeHeader,
                            HttpServletResponse response) {
        try {
            musicService.streamAudioToResponse(pathId, subPath, rangeHeader, response);
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
            @RequestParam(required = false, defaultValue = "0") int duration) {

        // 1. Look for a .lrc sidecar file next to the audio file
        String lrc = musicService.findLrcSidecar(pathId, subPath);
        if (lrc != null) {
            return ResponseEntity.ok(Map.of("source", "file", "lrc", lrc));
        }

        // 2. Fallback: query LRCLIB (free, no key required)
        if (title != null && !title.isBlank()) {
            try {
                String url = "https://lrclib.net/api/get?track_name=" +
                        java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8) +
                        (artist != null && !artist.isBlank()
                                ? "&artist_name=" + java.net.URLEncoder.encode(artist, java.nio.charset.StandardCharsets.UTF_8)
                                : "") +
                        (duration > 0 ? "&duration=" + duration : "");

                RestTemplate rt = new RestTemplate();
                HttpHeaders h = new HttpHeaders();
                h.set("User-Agent", "EverLoad/1.0 (https://github.com/everload)");
                ResponseEntity<Map> resp = rt.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class);

                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    Object syncedLyrics = resp.getBody().get("syncedLyrics");
                    Object plainLyrics  = resp.getBody().get("plainLyrics");
                    if (syncedLyrics != null) {
                        return ResponseEntity.ok(Map.of("source", "lrclib", "lrc", syncedLyrics));
                    }
                    if (plainLyrics != null) {
                        return ResponseEntity.ok(Map.of("source", "lrclib_plain", "plain", plainLyrics));
                    }
                }
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok(Map.of("source", "none"));
    }
}
