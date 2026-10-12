package com.everload.everload.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceServiceTest {

    @TempDir
    Path tempDir;

    private MaintenanceService service;
    private Path flag;

    @BeforeEach
    void setUp() {
        service = new MaintenanceService();
        flag = tempDir.resolve("nested/maintenance.flag");
        ReflectionTestUtils.setField(service, "flagFilePath", flag.toString());
    }

    @Test
    void activatePersistsCustomMessageAndStatus() throws Exception {
        service.activate("Planned maintenance");

        assertTrue(service.isActive());
        assertEquals("Planned maintenance", service.getMessage());
        assertEquals("Planned maintenance", Files.readString(flag));
        assertTrue(service.getStatus().isActive());
        assertEquals("Planned maintenance", service.getStatus().getMessage());
    }

    @Test
    void blankMessagesUseDefaultAndUpdateOnlyPersistsWhileActive() throws Exception {
        service.activate(" ");
        String defaultMessage = service.getMessage();
        assertFalse(defaultMessage.isBlank());
        assertEquals(defaultMessage, Files.readString(flag));

        service.deactivate();
        service.updateMessage("Changed message");
        assertEquals("Changed message", service.getMessage());
        assertFalse(Files.exists(flag));
        service.activate("Custom");
        service.updateMessage("Changed message");
        assertEquals("Changed message", Files.readString(flag));
        service.updateMessage("  ");
        assertEquals("Changed message", service.getMessage());
    }

    @Test
    void deactivateRemovesFlagAndKeepsLastMessage() throws Exception {
        service.activate("Temporary");

        service.deactivate();

        assertFalse(service.isActive());
        assertEquals("Temporary", service.getMessage());
        assertFalse(Files.exists(flag));
    }

    @Test
    void loadFromDiskRestoresMessageAndFallsBackForBlankContent() throws Exception {
        Files.createDirectories(flag.getParent());
        Files.writeString(flag, "Restored message\nignored second line");
        MaintenanceService restored = new MaintenanceService();
        ReflectionTestUtils.setField(restored, "flagFilePath", flag.toString());

        restored.loadFromDisk();

        assertTrue(restored.isActive());
        assertEquals("Restored message", restored.getMessage());

        Files.writeString(flag, "  \nother");
        MaintenanceService blank = new MaintenanceService();
        ReflectionTestUtils.setField(blank, "flagFilePath", flag.toString());
        blank.loadFromDisk();
        assertTrue(blank.isActive());
        assertFalse(blank.getMessage().isBlank());
    }

    @Test
    void loadFromDiskIgnoresMissingFlagAndReadFailure() throws Exception {
        service.loadFromDisk();
        assertFalse(service.isActive());

        Files.createDirectories(flag.getParent());
        Files.createDirectory(flag);
        service.loadFromDisk();
        assertFalse(service.isActive());
    }

    @Test
    void persistenceFailuresDoNotPreventStateChanges() throws Exception {
        Files.createDirectories(flag.getParent());
        Files.createDirectory(flag);

        service.activate("Still active");
        assertTrue(service.isActive());
        assertEquals("Still active", service.getMessage());

        Files.writeString(flag.resolve("child"), "keep directory non-empty");
        service.deactivate();
        assertFalse(service.isActive());
        assertTrue(Files.exists(flag));
    }
}
