package com.EverLoad.everload.service;

import com.EverLoad.everload.model.User;
import com.EverLoad.everload.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvatarService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    @Value("${avatar.storage.path}")
    private String storagePath;

    @Value("${avatar.max-size-mb:5}")
    private long maxSizeMb;

    private final UserRepository userRepository;

    public String uploadAvatar(String username, MultipartFile file) throws IOException {
        validateFile(file);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Borrar avatar anterior si existe
        deleteAvatarFile(user.getAvatarFilename());

        // Generar nombre único
        String ext = getExtension(file.getOriginalFilename());
        String filename = "avatar_" + user.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

        Path dir = Path.of(storagePath);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

        user.setAvatarFilename(filename);
        userRepository.save(user);

        return filename;
    }

    public void removeAvatar(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        deleteAvatarFile(user.getAvatarFilename());
        user.setAvatarFilename(null);
        userRepository.save(user);
    }

    public Path getAvatarPath(String filename) {
        Path path = Path.of(storagePath).resolve(filename).normalize();
        if (!path.startsWith(Path.of(storagePath).normalize())) {
            throw new SecurityException("Acceso denegado");
        }
        return path;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Tipo de archivo no permitido. Usa JPG, PNG, WebP o GIF");
        }
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("El archivo supera el tamaño máximo de " + maxSizeMb + " MB");
        }
    }

    private void deleteAvatarFile(String filename) {
        if (filename == null || filename.isBlank()) return;
        try {
            Path path = Path.of(storagePath).resolve(filename).normalize();
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }

    private String getExtension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : ".jpg";
    }
}