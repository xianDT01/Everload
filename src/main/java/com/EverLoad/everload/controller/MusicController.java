package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.MusicMetadataDto;
import com.EverLoad.everload.service.MusicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Music Center", description = "Explorador musical y streaming de audio desde NAS")
@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
public class MusicController {

    private final MusicService musicService;

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Operation(summary = "Listar archivos de audio con metadatos ID3")
    @GetMapping("/metadata")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> browseMusic(@RequestParam Long pathId,
                                         @RequestParam(required = false) String subPath) {
        try {
            List<MusicMetadataDto> files = musicService.listFilesWithMetadata(pathId, subPath);
            return ResponseEntity.ok(files);
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
            musicService.updateMetadata(req.getPathId(), req.getRelativePath(), req.getTitle(), req.getArtist());
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
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
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

    @Operation(summary = "Carátula embebida en los tags ID3 del archivo de audio")
    @GetMapping("/cover")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
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
}