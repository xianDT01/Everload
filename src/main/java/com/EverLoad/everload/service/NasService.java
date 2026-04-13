package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.NasFileDto;
import com.EverLoad.everload.dto.NasPathDto;
import com.EverLoad.everload.model.NasPath;
import com.EverLoad.everload.repository.NasPathRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NasService {

    private final NasPathRepository nasPathRepository;

    @Value("${nas.storage.base}")
    private String nasStorageBase;

    // ── Gestión de rutas NAS ──────────────────────────────────────────────────

    public List<NasPathDto> getAllPaths() {
        return nasPathRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public NasPathDto createPath(NasPathDto dto) {
        if (nasPathRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Ya existe una ruta con ese nombre");
        }
        String resolvedPath = resolveSafePath(dto.getPath());
        NasPath nasPath = NasPath.builder()
                .name(dto.getName())
                .path(resolvedPath)
                .description(dto.getDescription())
                .build();
        return toDto(nasPathRepository.save(nasPath));
    }

    public void deletePath(Long id) {
        if (!nasPathRepository.existsById(id)) {
            throw new IllegalArgumentException("Ruta no encontrada");
        }
        nasPathRepository.deleteById(id);
    }

    // ── Explorador de archivos NAS ────────────────────────────────────────────

    public List<NasFileDto> listFiles(Long pathId, String subPath) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada"));

        Path basePath = Path.of(nasPath.getPath()).normalize();
        Path targetPath = subPath != null && !subPath.isBlank()
                ? basePath.resolve(subPath).normalize()
                : basePath;

        // Seguridad: evitar path traversal
        if (!targetPath.startsWith(basePath)) {
            throw new SecurityException("Acceso denegado: path traversal detectado");
        }

        File dir = targetPath.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            return Collections.emptyList();
        }
        if (!dir.canRead()) {
            throw new SecurityException("Sin permisos de lectura en: " + targetPath);
        }

        File[] files = dir.listFiles();
        if (files == null) return Collections.emptyList();

        return Arrays.stream(files)
                .map(f -> NasFileDto.builder()
                        .name(f.getName())
                        .path(basePath.relativize(f.toPath()).toString())
                        .directory(f.isDirectory())
                        .size(f.isFile() ? f.length() : 0)
                        .lastModified(formatDate(f.lastModified()))
                        .build())
                .sorted((a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .collect(Collectors.toList());
    }

    public void createFolder(Long pathId, String subPath, String folderName) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada"));

        Path basePath = Path.of(nasPath.getPath()).normalize();
        Path parentPath = subPath != null && !subPath.isBlank()
                ? basePath.resolve(subPath).normalize()
                : basePath;

        if (!parentPath.startsWith(basePath)) {
            throw new SecurityException("Acceso denegado: path traversal detectado");
        }

        Path newFolder = parentPath.resolve(sanitizeName(folderName)).normalize();
        if (!newFolder.startsWith(basePath)) {
            throw new SecurityException("Acceso denegado");
        }

        try {
            Files.createDirectories(newFolder);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear la carpeta: " + e.getMessage());
        }
    }

    public String renameFileOrFolder(Long pathId, String relativePath, String newName) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada"));
        Path basePath = Path.of(nasPath.getPath()).normalize();
        Path target = basePath.resolve(relativePath).normalize();

        if (!target.startsWith(basePath)) throw new SecurityException("Acceso denegado: path traversal");
        if (target.equals(basePath)) throw new SecurityException("No se puede renombrar la raíz");
        if (!target.toFile().exists()) throw new IllegalArgumentException("Archivo/carpeta no encontrado");

        Path destination = target.resolveSibling(sanitizeName(newName)).normalize();
        if (!destination.startsWith(basePath)) throw new SecurityException("Acceso denegado");
        if (destination.toFile().exists()) throw new IllegalArgumentException("Ya existe un elemento con ese nombre");

        try {
            Files.move(target, destination);
            return basePath.relativize(destination).toString();
        } catch (IOException e) {
            throw new RuntimeException("No se pudo renombrar: " + e.getMessage());
        }
    }

    public void moveFileOrFolder(Long pathId, String sourcePath, String targetFolderPath) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada"));
        Path basePath = Path.of(nasPath.getPath()).normalize();
        Path source = basePath.resolve(sourcePath).normalize();
        Path targetDir = (targetFolderPath != null && !targetFolderPath.isBlank())
                ? basePath.resolve(targetFolderPath).normalize()
                : basePath;

        if (!source.startsWith(basePath)) throw new SecurityException("Acceso denegado: origen");
        if (!targetDir.startsWith(basePath)) throw new SecurityException("Acceso denegado: destino");
        if (!source.toFile().exists()) throw new IllegalArgumentException("Origen no encontrado");
        if (!targetDir.toFile().isDirectory()) throw new IllegalArgumentException("El destino no es una carpeta");
        if (source.equals(targetDir)) throw new IllegalArgumentException("Origen y destino son iguales");
        if (targetDir.startsWith(source)) throw new IllegalArgumentException("No se puede mover dentro de sí mismo");

        Path destination = targetDir.resolve(source.getFileName()).normalize();
        if (destination.toFile().exists()) throw new IllegalArgumentException("Ya existe un elemento con ese nombre en el destino");

        try {
            Files.move(source, destination);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo mover: " + e.getMessage());
        }
    }

    public void saveFolderCover(Long pathId, String folderPath, byte[] imageData, String contentType) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada"));
        Path basePath = Path.of(nasPath.getPath()).normalize();
        Path folder = (folderPath != null && !folderPath.isBlank())
                ? basePath.resolve(folderPath).normalize()
                : basePath;

        if (!folder.startsWith(basePath)) throw new SecurityException("Acceso denegado");
        if (!folder.toFile().isDirectory()) throw new IllegalArgumentException("No es una carpeta");

        String ext = (contentType != null && contentType.contains("png")) ? ".png" : ".jpg";
        Path coverPath = folder.resolve("cover" + ext);
        try {
            Files.write(coverPath, imageData);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar la portada: " + e.getMessage());
        }
    }

    public void deleteFileOrFolder(Long pathId, String relativePath) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada"));

        Path basePath = Path.of(nasPath.getPath()).normalize();
        Path target = basePath.resolve(relativePath).normalize();

        if (!target.startsWith(basePath)) {
            throw new SecurityException("Acceso denegado: path traversal detectado");
        }
        if (target.equals(basePath)) {
            throw new SecurityException("No se puede eliminar la carpeta raíz");
        }

        try {
            deleteRecursively(target);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo eliminar: " + e.getMessage());
        }
    }

    /**
     * Mueve un archivo temporal al NAS. Retorna la ruta final.
     */
    public String saveToNas(Long pathId, String subPath, Path tempFile, String fileName) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada"));

        Path basePath = Path.of(nasPath.getPath()).normalize();
        Path destDir = subPath != null && !subPath.isBlank()
                ? basePath.resolve(subPath).normalize()
                : basePath;

        if (!destDir.startsWith(basePath)) {
            throw new SecurityException("Acceso denegado: path traversal detectado");
        }

        if (!destDir.toFile().canWrite()) {
            throw new SecurityException("Sin permisos de escritura en: " + destDir);
        }

        Path dest = destDir.resolve(sanitizeName(fileName)).normalize();
        if (!dest.startsWith(basePath)) {
            throw new SecurityException("Acceso denegado");
        }

        try {
            Files.createDirectories(destDir);
            Files.copy(tempFile, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar archivo en NAS: " + e.getMessage());
        }
    }

    // ── Shared path helpers (used by MusicService and others) ────────────────

    /**
     * Resolves pathId + optional subPath to an absolute Path, validating no traversal.
     * Returns the base path when subPath is blank/null.
     */
    public Path resolveValidatedPath(Long pathId, String subPath) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada: " + pathId));
        Path basePath = Path.of(nasPath.getPath()).normalize();
        Path target = (subPath != null && !subPath.isBlank())
                ? basePath.resolve(subPath).normalize()
                : basePath;
        if (!target.startsWith(basePath)) {
            throw new SecurityException("Acceso denegado: path traversal detectado");
        }
        return target;
    }

    /** Returns the configured base path for a NAS entry. */
    public Path getBasePath(Long pathId) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada: " + pathId));
        return Path.of(nasPath.getPath()).normalize();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NasPathDto toDto(NasPath nasPath) {
        File dir = new File(nasPath.getPath());
        return NasPathDto.builder()
                .id(nasPath.getId())
                .name(nasPath.getName())
                .path(nasPath.getPath())
                .description(nasPath.getDescription())
                .readable(dir.canRead())
                .writable(dir.canWrite())
                .build();
    }

    private String resolveSafePath(String inputPath) {
        Path base = Path.of(nasStorageBase).normalize();
        Path resolved = base.resolve(inputPath).normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("Ruta no permitida fuera del almacenamiento NAS base");
        }
        return resolved.toString();
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9._\\-() ]", "_");
    }

    private String formatDate(long epochMillis) {
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}