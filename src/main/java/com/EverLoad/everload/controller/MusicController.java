package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.MusicMetadataDto;
import com.EverLoad.everload.service.MusicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
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

    // ── Streaming ─────────────────────────────────────────────────────────────

    /**
     * Serves audio with proper Range support.
     * - No Range header  → HTTP 200 with full resource + Accept-Ranges: bytes
     * - Range header     → HTTP 206 Partial Content with ResourceRegion
     *
     * The JWT token can be passed as ?token= query param because HTMLAudioElement
     * cannot set custom request headers.
     */
    @Operation(summary = "Streaming de audio con soporte Accept-Ranges")
    @GetMapping("/stream")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> streamAudio(@RequestParam Long pathId,
                                         @RequestParam String subPath,
                                         @RequestHeader HttpHeaders requestHeaders) {
        try {
            Resource resource = musicService.getAudioResource(pathId, subPath);
            MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            List<HttpRange> ranges = requestHeaders.getRange();

            if (ranges.isEmpty()) {
                // Full delivery — tells the browser we support ranges for future seeks
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .body(resource);
            }

            // Partial content
            ResourceRegion region = musicService.streamAudio(pathId, subPath, requestHeaders);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(region);

        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
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
                headers.setCacheControl("public, max-age=86400"); // 24h cache
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
    public ResponseEntity<?> streamYoutubeAudio(@RequestParam String videoId,
                                                @RequestHeader HttpHeaders requestHeaders) {
        try {
            Resource resource = musicService.getYoutubeAudioResource(videoId);
            MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            List<HttpRange> ranges = requestHeaders.getRange();

            if (ranges.isEmpty()) {
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .body(resource);
            }

            ResourceRegion region = musicService.streamYoutubeAudio(videoId, requestHeaders);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(region);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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