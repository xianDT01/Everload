package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.AvatarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Tag(name = "Avatar", description = "Gestión de imagen de perfil de usuario")
@RestController
@RequestMapping("/api/user/avatar")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    @Operation(summary = "Subir o reemplazar avatar")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file,
                                          Authentication auth) {
        try {
            String filename = avatarService.uploadAvatar(auth.getName(), file);
            return ResponseEntity.ok(Map.of(
                    "message", "Avatar actualizado",
                    "avatarUrl", "/api/user/avatar/img/" + filename
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al guardar el archivo"));
        }
    }

    @Operation(summary = "Eliminar avatar")
    @DeleteMapping
    public ResponseEntity<?> removeAvatar(Authentication auth) {
        try {
            avatarService.removeAvatar(auth.getName());
            return ResponseEntity.ok(Map.of("message", "Avatar eliminado"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Obtener imagen de avatar por nombre de archivo")
    @GetMapping("/img/{filename}")
    public ResponseEntity<Resource> getAvatarImage(@PathVariable String filename) {
        try {
            Path path = avatarService.getAvatarPath(filename);
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            String contentType = Files.probeContentType(path);
            if (contentType == null) contentType = "image/jpeg";

            Resource resource = new FileSystemResource(path);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                    .body(resource);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}