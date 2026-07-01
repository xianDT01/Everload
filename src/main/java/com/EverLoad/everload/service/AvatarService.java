package com.EverLoad.everload.service;

import com.EverLoad.everload.model.User;
import com.EverLoad.everload.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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

    public String uploadGroupAvatar(Long groupId, String oldFilename, MultipartFile file) throws IOException {
        validateFile(file);

        // Borrar avatar anterior si existe
        deleteAvatarFile(oldFilename);

        // Generar nombre único
        String ext = getExtension(file.getOriginalFilename());
        String filename = "group_" + groupId + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

        Path dir = Path.of(storagePath);
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);

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
        String safeFilename = filename == null ? "" : filename.replaceFirst("^/+", "");
        Path root = Path.of(storagePath).normalize();
        Path path = root.resolve(safeFilename).normalize();
        if (!path.startsWith(root)) {
            throw new SecurityException("Acceso denegado");
        }
        return path;
    }

    public List<String> listAvatarImageUrls() throws IOException {
        Path root = Path.of(storagePath).normalize();
        if (!Files.exists(root)) return List.of();

        // Avatares de usuario (nivel superior) + fotos de artista persistentes ("artists/").
        // Se excluye la caché volátil "artists-auto" (se regenera sola y crecería a cientos).
        try (Stream<Path> paths = Files.walk(root, 2)) {
            List<Path> sortedImages = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isImageFile)
                    .filter(p -> {
                        Path parent = p.getParent();
                        String dirName = parent == null ? "" : parent.getFileName().toString();
                        return !"artists-auto".equals(dirName);
                    })
                    .sorted(Comparator.comparing(this::lastModifiedMillis).reversed())
                    .toList();

            Map<String, Path> uniqueImages = new LinkedHashMap<>();
            for (Path path : sortedImages) {
                uniqueImages.putIfAbsent(imageFingerprint(path), path);
            }

            return uniqueImages.values().stream()
                    .map(path -> root.relativize(path).toString().replace(File.separatorChar, '/'))
                    .map(relative -> "/api/user/avatar/img/" + relative)
                    .toList();
        }
    }

    private boolean isImageFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return filename.endsWith(".jpg") || filename.endsWith(".jpeg")
                || filename.endsWith(".png") || filename.endsWith(".webp")
                || filename.endsWith(".gif");
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private String imageFingerprint(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return bytesToHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
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
