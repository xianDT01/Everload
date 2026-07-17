package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.BackupDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BackupServiceTest {

    @TempDir
    Path tempDir;

    private BackupService service;

    @BeforeEach
    void setUp() {
        service = new BackupService(mock(DataSource.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "backupPath", tempDir.toString());
        ReflectionTestUtils.setField(service, "retention", 5);
    }

    @Test
    void listBackupsReturnsOnlyValidBackupArchivesNewestFirst() throws Exception {
        Path older = createZip("backup_database_2026-01-01_10-00-00.zip");
        Path newer = createZip("backup_full_2026-01-02_10-00-00.zip");
        Files.writeString(tempDir.resolve("notes.txt"), "ignored");
        Files.setLastModifiedTime(older, java.nio.file.attribute.FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(2_000));

        List<BackupDto> backups = service.listBackups();

        assertEquals(2, backups.size());
        assertEquals(newer.getFileName().toString(), backups.get(0).getName());
        assertTrue(backups.get(0).getSizeBytes() > 0);
    }

    @Test
    void deleteRemovesValidatedBackupAndRejectsUnsafeNames() throws Exception {
        Path backup = createZip("backup_database_2026-01-01_10-00-00.zip");

        service.delete(backup.getFileName().toString());

        assertFalse(Files.exists(backup));
        assertThrows(IllegalArgumentException.class, () -> service.delete("../backup.zip"));
        assertThrows(IllegalArgumentException.class, () -> service.delete("ordinary.zip"));
    }

    @Test
    void retentionConfigurationCanBeUpdated() {
        assertEquals(tempDir.toString(), service.getBackupPath());
        assertEquals(5, service.getRetention());
        service.setRetention(8);
        assertEquals(8, service.getRetention());
    }

    @Test
    void quickBackupRoundTripRestoresDatabaseAndConfiguredFiles() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:backup-roundtrip;DB_CLOSE_DELAY=-1");
        BackupService roundTrip = new BackupService(dataSource, new ObjectMapper());
        Path backupDir = Files.createDirectory(tempDir.resolve("backups"));
        Path avatars = Files.createDirectory(tempDir.resolve("avatars"));
        Path avatar = Files.writeString(avatars.resolve("user.jpg"), "avatar-data");
        Path config = Files.writeString(tempDir.resolve("config.json"), "{\"enabled\":true}");
        Path oldBackup = backupDir.resolve("backup_aaa.zip");
        Files.writeString(oldBackup, "old");
        ReflectionTestUtils.setField(roundTrip, "backupPath", backupDir.toString());
        ReflectionTestUtils.setField(roundTrip, "retention", 1);
        ReflectionTestUtils.setField(roundTrip, "avatarStoragePath", avatars.toString());
        ReflectionTestUtils.setField(roundTrip, "configPath", config.toString());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample(id INT PRIMARY KEY, content VARCHAR(20))");
            statement.execute("INSERT INTO sample VALUES (1, 'saved')");
        }

        BackupDto backup = roundTrip.createBackup(BackupService.BackupType.QUICK);
        assertTrue(Files.exists(backupDir.resolve(backup.getName())));
        assertFalse(Files.exists(oldBackup));

        Files.delete(avatar);
        Files.writeString(config, "changed");
        roundTrip.restore(backup.getName());

        assertEquals("avatar-data", Files.readString(avatar));
        assertEquals("{\"enabled\":true}", Files.readString(config));
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT content FROM sample WHERE id = 1")) {
            assertTrue(result.next());
            assertEquals("saved", result.getString(1));
        }
    }

    private Path createZip(String filename) throws Exception {
        Path path = tempDir.resolve(filename);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("sample.txt"));
            zip.write("backup".getBytes());
            zip.closeEntry();
        }
        return path;
    }
}
