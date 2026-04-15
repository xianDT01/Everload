package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.BackupDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages H2 database backups using H2's built-in SCRIPT/RUNSCRIPT commands.
 *
 * <p>Backup: {@code SCRIPT TO '/path/backup_timestamp.zip' COMPRESSION ZIP}
 * <p>Restore: {@code DROP ALL OBJECTS; RUNSCRIPT FROM '/path/...' COMPRESSION ZIP}
 *
 * <p>Restore works in-place without restarting Spring Boot, because H2 processes
 * the SQL on the same JDBC connection. Existing JPA sessions are stateless
 * (per-request EntityManager), so no stale caches remain after restore.
 */
@Slf4j
@Service
public class BackupService {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @Value("${app.backup.path:./backups}")
    private String backupPath;

    @Value("${app.backup.retention:10}")
    private int retention;

    private final DataSource dataSource;

    public BackupService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    public BackupDto createBackup() throws Exception {
        Path dir = ensureBackupDir();
        String filename = "backup_" + LocalDateTime.now().format(TS_FMT) + ".zip";
        Path dest = dir.resolve(filename);

        String safePath = toSqlPath(dest.toAbsolutePath());
        log.info("[BACKUP] Creating backup → {}", dest.toAbsolutePath());

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SCRIPT TO '" + safePath + "' COMPRESSION ZIP");
        }

        enforceRetention(dir);
        return buildDto(dest);
    }

    // ── Restore ────────────────────────────────────────────────────────────────

    public void restore(String filename) throws Exception {
        validateFilename(filename);
        Path backupFile = ensureBackupDir().resolve(filename);

        if (!Files.exists(backupFile)) {
            throw new IOException("Backup not found: " + filename);
        }

        String safePath = toSqlPath(backupFile.toAbsolutePath());
        log.warn("[BACKUP] Restoring database from {} — all current data will be REPLACED", filename);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
            stmt.execute("RUNSCRIPT FROM '" + safePath + "' COMPRESSION ZIP");
        }

        log.info("[BACKUP] Restore completed from {}", filename);
    }

    // ── List ───────────────────────────────────────────────────────────────────

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

    // ── Delete ─────────────────────────────────────────────────────────────────

    public void delete(String filename) throws IOException {
        validateFilename(filename);
        Path file = ensureBackupDir().resolve(filename);
        Files.deleteIfExists(file);
        log.info("[BACKUP] Deleted backup: {}", filename);
    }

    // ── Retention enforcement ──────────────────────────────────────────────────

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

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Path ensureBackupDir() throws IOException {
        Path dir = Path.of(backupPath);
        Files.createDirectories(dir);
        return dir;
    }

    /** Convert an absolute Path to a safe H2 SQL path string (forward slashes, escaped quotes). */
    private String toSqlPath(Path absolute) {
        return absolute.toString()
                .replace("\\", "/")  // H2 accepts forward slashes on all platforms
                .replace("'", "''"); // escape single quotes in SQL string literal
    }

    /** Prevent path-traversal: filename must match backup_*.zip, no directory separators. */
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
        try {
            BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
            Instant creationTime = attrs.lastModifiedTime().toInstant();
            String createdAt = creationTime.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            return BackupDto.builder()
                    .name(p.getFileName().toString())
                    .sizeBytes(attrs.size())
                    .createdAt(createdAt)
                    .build();
        } catch (IOException e) {
            return BackupDto.builder().name(p.getFileName().toString()).build();
        }
    }

    public String getBackupPath() { return backupPath; }
    public int getRetention()     { return retention; }
    public void setRetention(int r) { this.retention = r; }
}