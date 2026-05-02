package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.BackupDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class BackupService {

    public enum BackupType {
        QUICK,
        COMPLETE_APP,
        COMPLETE_TOTAL
    }

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static final String MANIFEST = "everload-backup.json";
    private static final String DATABASE_ENTRY = "database/everload-db.zip";

    @Value("${app.backup.path:./backups}")
    private String backupPath;

    @Value("${app.backup.retention:10}")
    private int retention;

    @Value("${avatar.storage.path:./avatars}")
    private String avatarStoragePath;

    @Value("${app.android.release-path:./android-release}")
    private String androidReleasePath;

    @Value("${app.config.path:./config.json}")
    private String configPath;

    @Value("${app.maintenance.flag-path:./maintenance.flag}")
    private String maintenanceFlagPath;

    @Value("${nas.storage.base:/app/nas_storage}")
    private String nasStoragePath;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public BackupService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public BackupDto createBackup() throws Exception {
        return createBackup(BackupType.QUICK);
    }

    public BackupDto createBackup(BackupType type) throws Exception {
        BackupType backupType = type == null ? BackupType.QUICK : type;
        Path dir = ensureBackupDir();
        String filename = "backup_" + backupType.name().toLowerCase() + "_" + LocalDateTime.now().format(TS_FMT) + ".zip";
        Path dest = dir.resolve(filename);
        Path tempDb = Files.createTempFile("everload-db-backup-", ".zip");

        try {
            createDatabaseScript(tempDb);
            try (OutputStream out = Files.newOutputStream(dest);
                 ZipOutputStream zip = new ZipOutputStream(out)) {
                writeManifest(zip, backupType);
                addFile(zip, tempDb, DATABASE_ENTRY);
                addConfiguredFiles(zip, backupType);
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }

        enforceRetention(dir);
        return buildDto(dest);
    }

    public void restore(String filename) throws Exception {
        validateFilename(filename);
        Path backupFile = ensureBackupDir().resolve(filename);
        if (!Files.exists(backupFile)) {
            throw new IOException("Backup not found: " + filename);
        }

        if (!isCompositeBackup(backupFile)) {
            restoreLegacyDatabaseBackup(backupFile);
            return;
        }

        Path tempDir = Files.createTempDirectory("everload-restore-");
        try {
            restoreCompositeBackup(backupFile, tempDir);
        } finally {
            deleteDirectory(tempDir);
        }
    }

    public List<BackupDto> listBackups() throws IOException {
        Path dir = ensureBackupDir();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("backup_")
                            && p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.reverseOrder())
                    .map(this::buildDto)
                    .collect(Collectors.toList());
        }
    }

    public void delete(String filename) throws IOException {
        validateFilename(filename);
        Path file = ensureBackupDir().resolve(filename);
        Files.deleteIfExists(file);
        log.info("[BACKUP] Deleted backup: {}", filename);
    }

    private void createDatabaseScript(Path dest) throws Exception {
        String safePath = toSqlPath(dest.toAbsolutePath());
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SCRIPT TO '" + safePath + "' COMPRESSION ZIP");
        }
    }

    private void restoreCompositeBackup(Path backupFile, Path tempDir) throws Exception {
        Map<String, Object> manifest = readManifest(backupFile);
        Path dbBackup = tempDir.resolve("everload-db.zip");

        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            ZipEntry dbEntry = zipFile.getEntry(DATABASE_ENTRY);
            if (dbEntry == null) throw new IOException("La copia no contiene base de datos");
            try (InputStream in = zipFile.getInputStream(dbEntry)) {
                Files.copy(in, dbBackup, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        restoreDatabaseScript(dbBackup);

        Object type = manifest.get("type");
        String backupType = String.valueOf(type);

        restoreDirectorySection(backupFile, "avatars/", Path.of(avatarStoragePath));
        restoreFileSection(backupFile, "config/config.json", Path.of(configPath));

        if (BackupType.COMPLETE_APP.name().equals(backupType) || BackupType.COMPLETE_TOTAL.name().equals(backupType)) {
            restoreDirectorySection(backupFile, "android-release/", Path.of(androidReleasePath));
            restoreFileSection(backupFile, "config/maintenance.flag", Path.of(maintenanceFlagPath), true);
        }

        if (BackupType.COMPLETE_TOTAL.name().equals(backupType)) {
            restoreDirectorySection(backupFile, "nas-storage/", Path.of(nasStoragePath));
        }

        log.info("[BACKUP] Composite restore completed from {}", backupFile.getFileName());
    }

    private void restoreLegacyDatabaseBackup(Path backupFile) throws Exception {
        log.warn("[BACKUP] Restoring legacy database-only backup from {}", backupFile.getFileName());
        restoreDatabaseScript(backupFile);
    }

    private void restoreDatabaseScript(Path dbBackup) throws Exception {
        String safePath = toSqlPath(dbBackup.toAbsolutePath());
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("RUNSCRIPT FROM '" + safePath + "' COMPRESSION ZIP");
        }
    }

    private void addConfiguredFiles(ZipOutputStream zip, BackupType type) throws IOException {
        addDirectory(zip, Path.of(avatarStoragePath), "avatars/");
        addFileIfExists(zip, Path.of(configPath), "config/config.json");

        if (type == BackupType.COMPLETE_APP || type == BackupType.COMPLETE_TOTAL) {
            addDirectory(zip, Path.of(androidReleasePath), "android-release/");
            addFileIfExists(zip, Path.of(maintenanceFlagPath), "config/maintenance.flag");
        }

        if (type == BackupType.COMPLETE_TOTAL) {
            addDirectory(zip, Path.of(nasStoragePath), "nas-storage/");
        }
    }

    private void writeManifest(ZipOutputStream zip, BackupType type) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "everload-composite-backup");
        manifest.put("formatVersion", 1);
        manifest.put("type", type.name());
        manifest.put("createdAt", Instant.now().toString());
        manifest.put("description", describe(type));
        zip.putNextEntry(new ZipEntry(MANIFEST));
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
        zip.write(json);
        zip.closeEntry();
    }

    private boolean isCompositeBackup(Path backupFile) {
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            return zipFile.getEntry(MANIFEST) != null && zipFile.getEntry(DATABASE_ENTRY) != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private Map<String, Object> readManifest(Path backupFile) {
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            ZipEntry entry = zipFile.getEntry(MANIFEST);
            if (entry == null) return Map.of();
            try (InputStream in = zipFile.getInputStream(entry)) {
                return objectMapper.readValue(in, Map.class);
            }
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    private void addDirectory(ZipOutputStream zip, Path source, String prefix) throws IOException {
        zip.putNextEntry(new ZipEntry(prefix));
        zip.closeEntry();
        if (!Files.isDirectory(source)) return;
        Path root = source.toAbsolutePath().normalize();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String relative = root.relativize(path).toString().replace("\\", "/");
                addFile(zip, path, prefix + relative);
            }
        }
    }

    private void addFileIfExists(ZipOutputStream zip, Path source, String entryName) throws IOException {
        if (Files.isRegularFile(source)) addFile(zip, source, entryName);
    }

    private void addFile(ZipOutputStream zip, Path source, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        Files.copy(source, zip);
        zip.closeEntry();
    }

    private void restoreDirectorySection(Path backupFile, String prefix, Path target) throws IOException {
        boolean present = false;
        Path normalizedTarget = target.toAbsolutePath().normalize();
        deleteDirectory(normalizedTarget);
        Files.createDirectories(normalizedTarget);

        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || name.equals(prefix)) continue;
                present = true;
                Path out = normalizedTarget.resolve(name.substring(prefix.length())).normalize();
                if (!out.startsWith(normalizedTarget)) throw new SecurityException("Entrada de backup invalida: " + name);
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    if (out.getParent() != null) Files.createDirectories(out.getParent());
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }

        if (!present) log.info("[BACKUP] Restored empty/missing section {}", prefix);
    }

    private void restoreFileSection(Path backupFile, String entryName, Path target) throws IOException {
        restoreFileSection(backupFile, entryName, target, false);
    }

    private void restoreFileSection(Path backupFile, String entryName, Path target, boolean deleteWhenMissing) throws IOException {
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            Path normalizedTarget = target.toAbsolutePath().normalize();
            if (entry == null) {
                if (deleteWhenMissing) Files.deleteIfExists(normalizedTarget);
                return;
            }
            if (normalizedTarget.getParent() != null) Files.createDirectories(normalizedTarget.getParent());
            try (InputStream in = zipFile.getInputStream(entry)) {
                Files.copy(in, normalizedTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void enforceRetention(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            List<Path> backups = stream
                    .filter(p -> p.getFileName().toString().startsWith("backup_")
                            && p.getFileName().toString().endsWith(".zip"))
                    .sorted()
                    .collect(Collectors.toList());

            while (backups.size() > retention) {
                Path oldest = backups.remove(0);
                Files.deleteIfExists(oldest);
                log.info("[BACKUP] Retention: removed old backup {}", oldest.getFileName());
            }
        }
    }

    private Path ensureBackupDir() throws IOException {
        Path dir = Path.of(backupPath);
        Files.createDirectories(dir);
        return dir;
    }

    private String toSqlPath(Path absolute) {
        return absolute.toString().replace("\\", "/").replace("'", "''");
    }

    private void validateFilename(String filename) {
        if (filename == null
                || filename.contains("..")
                || filename.contains("/")
                || filename.contains("\\")
                || !filename.startsWith("backup_")
                || !filename.endsWith(".zip")) {
            throw new IllegalArgumentException("Invalid backup filename: " + filename);
        }
    }

    private BackupDto buildDto(Path p) {
        Map<String, Object> manifest = isCompositeBackup(p) ? readManifest(p) : Map.of();
        String type = String.valueOf(manifest.getOrDefault("type", "LEGACY_DATABASE"));
        String description = String.valueOf(manifest.getOrDefault("description", "Base de datos antigua"));
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            String createdAt = attrs.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            return BackupDto.builder()
                    .name(p.getFileName().toString())
                    .sizeBytes(attrs.size())
                    .createdAt(createdAt)
                    .type(type)
                    .description(description)
                    .build();
        } catch (IOException e) {
            return BackupDto.builder().name(p.getFileName().toString()).type(type).description(description).build();
        }
    }

    private String describe(BackupType type) {
        return switch (type) {
            case QUICK -> "Rapida: base de datos, usuarios, chats, configuracion y avatares.";
            case COMPLETE_APP -> "Completa app: rapida + APK Android y archivos de estado de la aplicacion.";
            case COMPLETE_TOTAL -> "Completa total: completa app + archivos NAS, canciones y portadas.";
        };
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        if (dir.getParent() == null || dir.toString().length() < 4) {
            throw new IOException("Ruta de restauracion demasiado amplia: " + dir);
        }
        try (var walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    public String getBackupPath() { return backupPath; }
    public int getRetention() { return retention; }
    public void setRetention(int r) { this.retention = r; }
}
