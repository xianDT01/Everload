package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.NasFileDto;
import com.EverLoad.everload.dto.NasPathDto;
import com.EverLoad.everload.model.NasPath;
import com.EverLoad.everload.repository.NasPathRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class NasService {

    private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = Set.of(
            "mp3", "flac", "m4a", "wav", "ogg", "aac", "opus", "wma", "alac", "webm"
    );

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

    // ── Upload / Download ─────────────────────────────────────────────────────

    /**
     * @param paths Optional list of relative paths (one per file), used when uploading a folder
     *              to preserve the directory structure (e.g. "Beatles/Abbey Road/01-ComeT.mp3").
     *              When null or shorter than files list, files are placed flat in destDir.
     */
    public List<Map<String, Object>> uploadMusicFiles(Long pathId, String subPath,
                                                       List<MultipartFile> files,
                                                       List<String> paths) {
        NasPath nasPath = nasPathRepository.findById(pathId)
                .orElseThrow(() -> new IllegalArgumentException("Ruta NAS no encontrada"));
        Path basePath = Path.of(nasPath.getPath()).normalize();
        Path destDir = subPath != null && !subPath.isBlank()
                ? basePath.resolve(subPath).normalize()
                : basePath;
        if (!destDir.startsWith(basePath)) throw new SecurityException("Acceso denegado: path traversal detectado");

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String originalName = file.getOriginalFilename();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", originalName != null ? originalName : file.getName());
            try {
                if (originalName == null || !isAllowedAudioExtension(originalName)) {
                    result.put("status", "error");
                    result.put("message", "Formato no permitido");
                    results.add(result);
                    continue;
                }
                // If a relative path was provided (folder upload), recreate directory structure
                Path dest;
                String relPath = (paths != null && i < paths.size()) ? paths.get(i) : null;
                if (relPath != null && !relPath.isBlank()) {
                    String safePath = buildSafeRelativePath(relPath);
                    dest = destDir.resolve(safePath).normalize();
                } else {
                    dest = destDir.resolve(sanitizeName(originalName)).normalize();
                }
                if (!dest.startsWith(basePath)) throw new SecurityException("Acceso denegado");
                Files.createDirectories(dest.getParent());
                Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
                result.put("status", "ok");
                result.put("path", basePath.relativize(dest).toString());
            } catch (Exception e) {
                result.put("status", "error");
                result.put("message", e.getMessage());
            }
            results.add(result);
        }
        return results;
    }

    /** Sanitizes each segment of a relative path like "FolderA/SubB/file.mp3". */
    private String buildSafeRelativePath(String relPath) {
        String[] segments = relPath.replace('\\', '/').split("/");
        return Arrays.stream(segments)
                .filter(s -> !s.isBlank())
                .map(this::sanitizeName)
                .collect(Collectors.joining("/"));
    }

    public void downloadFileToResponse(Long pathId, String relativePath, HttpServletResponse response) throws IOException {
        Path file = resolveValidatedPath(pathId, relativePath);
        if (!file.toFile().isFile()) throw new IllegalArgumentException("No es un archivo");
        String fileName = file.getFileName().toString();
        response.setContentType(detectAudioMimeType(fileName));
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentLengthLong(Files.size(file));
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file, out);
        }
    }

    public void downloadFolderZipToResponse(Long pathId, String relativePath, HttpServletResponse response) throws IOException {
        Path folder = resolveValidatedPath(pathId, relativePath);
        if (!folder.toFile().isDirectory()) throw new IllegalArgumentException("No es una carpeta");
        String zipName = folder.getFileName() + ".zip";
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + zipName + "\"");
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            addFolderToZip(folder, folder.getParent(), zos);
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
        return name.replaceAll("[^\\p{L}\\p{N}._\\-() ]", "_");
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

    private boolean isAllowedAudioExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return false;
        return ALLOWED_AUDIO_EXTENSIONS.contains(filename.substring(dot + 1).toLowerCase());
    }

    private void addFolderToZip(Path path, Path baseDir, ZipOutputStream zos) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.sorted().toList()) {
                    addFolderToZip(child, baseDir, zos);
                }
            }
        } else {
            String entryName = baseDir.relativize(path).toString().replace('\\', '/');
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(path, zos);
            zos.closeEntry();
        }
    }

    private String detectAudioMimeType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        return switch (ext) {
            case "mp3"  -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "wav"  -> "audio/wav";
            case "ogg"  -> "audio/ogg";
            case "m4a"  -> "audio/mp4";
            case "aac"  -> "audio/aac";
            case "opus" -> "audio/opus";
            case "wma"  -> "audio/x-ms-wma";
            case "webm" -> "audio/webm";
            default     -> "application/octet-stream";
        };
    }
}