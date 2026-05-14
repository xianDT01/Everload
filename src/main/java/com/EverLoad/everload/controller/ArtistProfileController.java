package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.ArtistProfile;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.repository.ArtistProfileRepository;
import com.EverLoad.everload.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Artist profiles", description = "Perfiles manuales de artistas")
@RestController
@RequestMapping("/api/artists")
@RequiredArgsConstructor
public class ArtistProfileController {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final ArtistProfileRepository artistRepository;
    private final UserRepository userRepository;

    @Value("${avatar.storage.path:./avatars}")
    private String avatarStoragePath;

    @Value("${avatar.max-size-mb:5}")
    private long maxSizeMb;

    private User getUser(UserDetails ud) {
        return userRepository.findByUsername(ud.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Operation(summary = "Listar perfiles manuales de artistas")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<List<Map<String, Object>>> list(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(artistRepository.findByUserOrderByNameAsc(getUser(ud)).stream()
                .map(this::toDto)
                .toList());
    }

    @Operation(summary = "Crear artista manual")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal UserDetails ud,
                                                      @RequestBody ArtistProfileDto dto) {
        ArtistProfile profile = ArtistProfile.builder()
                .user(getUser(ud))
                .name(cleanName(dto.getName()))
                .aliases(clean(dto.getAliases()))
                .description(clean(dto.getDescription()))
                .build();
        return ResponseEntity.ok(toDto(artistRepository.save(profile)));
    }

    @Operation(summary = "Editar artista manual")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> update(@AuthenticationPrincipal UserDetails ud,
                                    @PathVariable Long id,
                                    @RequestBody ArtistProfileDto dto) {
        return artistRepository.findByIdAndUser(id, getUser(ud))
                .map(profile -> {
                    profile.setName(cleanName(dto.getName()));
                    profile.setAliases(clean(dto.getAliases()));
                    profile.setDescription(clean(dto.getDescription()));
                    return ResponseEntity.ok(toDto(artistRepository.save(profile)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Eliminar artista manual")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> delete(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return artistRepository.findByIdAndUser(id, getUser(ud))
                .map(profile -> {
                    deleteImage(profile.getImageFilename());
                    artistRepository.delete(profile);
                    return ResponseEntity.ok(Map.of("deleted", true));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Subir imagen manual de artista")
    @PostMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> uploadImage(@AuthenticationPrincipal UserDetails ud,
                                         @PathVariable Long id,
                                         @RequestParam("image") MultipartFile image) {
        return artistRepository.findByIdAndUser(id, getUser(ud))
                .map(profile -> {
                    try {
                        validateImage(image);
                        deleteImage(profile.getImageFilename());
                        String filename = saveImage(profile.getId(), image);
                        profile.setImageFilename(filename);
                        return ResponseEntity.ok(toDto(artistRepository.save(profile)));
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Guardar imagen de artista desde URL revisada")
    @PostMapping("/{id}/image-url")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> uploadImageFromUrl(@AuthenticationPrincipal UserDetails ud,
                                                @PathVariable Long id,
                                                @RequestBody ArtistImageUrlDto dto) {
        return artistRepository.findByIdAndUser(id, getUser(ud))
                .map(profile -> {
                    try {
                        deleteImage(profile.getImageFilename());
                        String filename = saveImageFromUrl(profile.getId(), dto.getImageUrl());
                        profile.setImageFilename(filename);
                        return ResponseEntity.ok(toDto(artistRepository.save(profile)));
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Quitar imagen manual de artista")
    @DeleteMapping("/{id}/image")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> removeImage(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return artistRepository.findByIdAndUser(id, getUser(ud))
                .map(profile -> {
                    deleteImage(profile.getImageFilename());
                    profile.setImageFilename(null);
                    return ResponseEntity.ok(toDto(artistRepository.save(profile)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Imagen pública de artista")
    @GetMapping("/image/{filename:.+}")
    public ResponseEntity<FileSystemResource> image(@PathVariable String filename) {
        Path path = artistImageDir().resolve(filename).normalize();
        if (!path.startsWith(artistImageDir().normalize()) || !Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(guessContentType(path)))
                .body(new FileSystemResource(path));
    }

    private Map<String, Object> toDto(ArtistProfile profile) {
        String imageUrl = profile.getImageFilename() == null || profile.getImageFilename().isBlank()
                ? ""
                : "/api/artists/image/" + profile.getImageFilename();
        return Map.of(
                "id", profile.getId(),
                "name", profile.getName(),
                "aliases", profile.getAliases() == null ? "" : profile.getAliases(),
                "description", profile.getDescription() == null ? "" : profile.getDescription(),
                "imageUrl", imageUrl
        );
    }

    private String cleanName(String name) {
        String value = clean(name);
        if (value.isBlank()) throw new IllegalArgumentException("El nombre es obligatorio");
        return value.length() > 200 ? value.substring(0, 200) : value;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("La imagen está vacía");
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Tipo de imagen no permitido");
        }
        if (file.getSize() > maxSizeMb * 1024 * 1024) {
            throw new IllegalArgumentException("La imagen supera " + maxSizeMb + " MB");
        }
    }

    private String saveImage(Long artistId, MultipartFile file) throws IOException {
        Files.createDirectories(artistImageDir());
        String ext = extension(file.getOriginalFilename());
        String filename = "artist_" + artistId + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Files.copy(file.getInputStream(), artistImageDir().resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return filename;
    }

    private String saveImageFromUrl(Long artistId, String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isBlank()) throw new IllegalArgumentException("URL de imagen vacía");
        URI uri = URI.create(imageUrl);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !host.endsWith("dzcdn.net")) {
            throw new IllegalArgumentException("Origen de imagen no permitido");
        }

        Files.createDirectories(artistImageDir());
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        String contentType = connection.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.split(";", 2)[0].trim())) {
            throw new IllegalArgumentException("Tipo de imagen no permitido");
        }

        String filename = "artist_" + artistId + "_" + UUID.randomUUID().toString().substring(0, 8) + extensionFromContentType(contentType);
        Path target = artistImageDir().resolve(filename);
        try (InputStream in = connection.getInputStream()) {
            long copied = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            if (copied > maxSizeMb * 1024 * 1024) {
                Files.deleteIfExists(target);
                throw new IllegalArgumentException("La imagen supera " + maxSizeMb + " MB");
            }
        }
        return filename;
    }

    private void deleteImage(String filename) {
        if (filename == null || filename.isBlank()) return;
        try {
            Files.deleteIfExists(artistImageDir().resolve(filename).normalize());
        } catch (IOException ignored) {}
    }

    private Path artistImageDir() {
        return Path.of(avatarStoragePath).resolve("artists");
    }

    private String extension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : ".jpg";
    }

    private String extensionFromContentType(String contentType) {
        String type = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase();
        return switch (type) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    private String guessContentType(Path path) {
        try {
            String type = Files.probeContentType(path);
            return type != null ? type : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } catch (IOException e) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    @Data
    static class ArtistProfileDto {
        private String name;
        private String aliases;
        private String description;
    }

    @Data
    static class ArtistImageUrlDto {
        private String imageUrl;
    }
}
