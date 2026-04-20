package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.AcoustIdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AcoustID", description = "Identificación de canciones por huella de audio (Chromaprint + AcoustID)")
@RestController
@RequestMapping("/api/music/fingerprint")
@RequiredArgsConstructor
public class AcoustIdController {

    private final AcoustIdService acoustIdService;

    @Operation(summary = "Identificar canción y embeber portada/metadatos por huella de audio")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<?> fingerprint(@RequestParam Long pathId,
                                         @RequestParam String subPath) {
        try {
            AcoustIdService.FingerprintResult result = acoustIdService.identify(pathId, subPath);
            return ResponseEntity.ok(Map.of(
                    "found",          result.found(),
                    "title",          result.title()          != null ? result.title()          : "",
                    "artist",         result.artist()         != null ? result.artist()         : "",
                    "album",          result.album()          != null ? result.album()          : "",
                    "coverEmbedded",  result.coverEmbedded(),
                    "tagsUpdated",    result.tagsUpdated(),
                    "error",          result.error()          != null ? result.error()          : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
