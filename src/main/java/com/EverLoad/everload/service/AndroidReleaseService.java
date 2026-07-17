package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.AndroidReleaseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AndroidReleaseService {

    private static final String DEFAULT_APK_FILENAME = "everload.apk";
    private static final String FILE_NAME_FIELD = "fileName";

    private final ObjectMapper objectMapper;

    @Value("${app.android.release-path:./android-release}")
    private String releasePath;

    public AndroidReleaseDto getRelease() {
        Path apk = apkPath();
        Map<String, String> metadata = readMetadata();
        boolean available = Files.isRegularFile(apk);
        long size = 0;
        try {
            if (available) size = Files.size(apk);
        } catch (IOException ignored) {
            // Metadata remains usable even if the filesystem cannot report the APK size.
        }

        return AndroidReleaseDto.builder()
                .available(available)
                .versionName(metadata.getOrDefault("versionName", ""))
                .versionCode(metadata.getOrDefault("versionCode", ""))
                .minAndroidVersion(metadata.getOrDefault("minAndroidVersion", "Android 8.0+"))
                .releaseNotes(metadata.getOrDefault("releaseNotes", ""))
                .fileName(metadata.getOrDefault(FILE_NAME_FIELD, available ? DEFAULT_APK_FILENAME : ""))
                .sizeBytes(size)
                .sizeFormatted(formatSize(size))
                .uploadedAt(metadata.getOrDefault("uploadedAt", ""))
                .downloadUrl(available ? "/api/app-release/android/download" : "")
                .build();
    }

    public AndroidReleaseDto saveRelease(MultipartFile file, String versionName, String versionCode,
                                         String minAndroidVersion, String releaseNotes) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("La APK no puede estar vacia");
        String originalName = sanitizeFileName(file.getOriginalFilename());
        if (!originalName.toLowerCase().endsWith(".apk")) {
            throw new IllegalArgumentException("El archivo debe ser una APK");
        }

        Files.createDirectories(basePath());
        Files.copy(file.getInputStream(), apkPath(), StandardCopyOption.REPLACE_EXISTING);

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("versionName", safe(versionName));
        metadata.put("versionCode", safe(versionCode));
        metadata.put("minAndroidVersion", safe(minAndroidVersion).isBlank() ? "Android 8.0+" : safe(minAndroidVersion));
        metadata.put("releaseNotes", safe(releaseNotes));
        metadata.put(FILE_NAME_FIELD, originalName);
        metadata.put("uploadedAt", Instant.now().toString());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(metadataPath().toFile(), metadata);

        return getRelease();
    }

    public Resource getApkResource() throws MalformedURLException {
        Path apk = apkPath();
        if (!Files.isRegularFile(apk)) throw new IllegalArgumentException("No hay APK publicada");
        return new UrlResource(apk.toUri());
    }

    public String getDownloadFileName() {
        String fileName = sanitizeFileName(readMetadata().getOrDefault(FILE_NAME_FIELD, DEFAULT_APK_FILENAME));
        return fileName.toLowerCase().endsWith(".apk") ? fileName : DEFAULT_APK_FILENAME;
    }

    public void deleteRelease() throws IOException {
        Files.deleteIfExists(apkPath());
        Files.deleteIfExists(metadataPath());
    }

    private Map<String, String> readMetadata() {
        Path metadata = metadataPath();
        if (!Files.isRegularFile(metadata)) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(metadata.toFile(), objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
        } catch (IOException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Path basePath() {
        return Path.of(releasePath).normalize();
    }

    private Path apkPath() {
        return basePath().resolve("everload-latest.apk").normalize();
    }

    private Path metadataPath() {
        return basePath().resolve("android-release.json").normalize();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String sanitizeFileName(String fileName) {
        String rawName = fileName == null ? "" : fileName.trim();
        if (rawName.isBlank()) return DEFAULT_APK_FILENAME;
        String safeName = Path.of(rawName).getFileName().toString().trim();
        safeName = safeName.replaceAll("[\\r\\n\\\"\\\\/]+", "-");
        return safeName.isBlank() ? DEFAULT_APK_FILENAME : safeName;
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(java.util.Locale.US, value >= 10 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}
