package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.BackupDto;
import com.EverLoad.everload.service.AuditLogService;
import com.EverLoad.everload.service.BackupService;
import com.EverLoad.everload.service.BackupService.BackupType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupControllerTest {

    private BackupService backupService;
    private AuditLogService auditLogService;
    private BackupController controller;

    @BeforeEach
    void setUp() {
        backupService = mock(BackupService.class);
        auditLogService = mock(AuditLogService.class);
        controller = new BackupController(backupService, auditLogService);
    }

    @Test
    void listReturnsBackupsAndHandlesFailure() throws Exception {
        BackupDto backup = backup();
        when(backupService.listBackups()).thenReturn(List.of(backup));

        ResponseEntity<List<BackupDto>> success = controller.list();

        assertEquals(HttpStatus.OK, success.getStatusCode());
        assertEquals(List.of(backup), success.getBody());

        when(backupService.listBackups()).thenThrow(new IllegalStateException("disk unavailable"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.list().getStatusCode());
    }

    @Test
    void createUsesRequestedTypeAndAuditsResult() throws Exception {
        BackupDto backup = backup();
        when(backupService.createBackup(BackupType.COMPLETE_APP)).thenReturn(backup);

        ResponseEntity<?> response = controller.create(Map.of("type", " complete_app "));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(backup, response.getBody());
        verify(auditLogService).log(
                "BACKUP_CREATED", "Database", "backup.zip",
                "Tipo: " + backup.getType() + " | Tamano: " + backup.getSizeFormatted()
        );
    }

    @Test
    void createDefaultsInvalidTypeAndReportsFailure() throws Exception {
        when(backupService.createBackup(BackupType.QUICK)).thenThrow(new IllegalStateException("database busy"));

        ResponseEntity<?> response = controller.create(Map.of("type", "unknown"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals("Error al crear la copia: database busy", body.get("error"));
    }

    @Test
    void restoreValidatesFilenameAndHandlesSuccessAndFailure() throws Exception {
        ResponseEntity<?> missing = controller.restore(Map.of());
        assertEquals(HttpStatus.BAD_REQUEST, missing.getStatusCode());

        ResponseEntity<?> success = controller.restore(Map.of("filename", "backup.zip"));
        assertEquals(HttpStatus.OK, success.getStatusCode());
        verify(backupService).restore("backup.zip");
        verify(auditLogService).log(
                "BACKUP_RESTORED", "Database", "backup.zip", "Copia restaurada correctamente"
        );

        doThrow(new IllegalStateException("invalid archive")).when(backupService).restore("bad.zip");
        ResponseEntity<?> failure = controller.restore(Map.of("filename", "bad.zip"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failure.getStatusCode());
    }

    @Test
    void deleteAuditsSuccessAndReportsFailure() throws Exception {
        ResponseEntity<?> success = controller.delete("backup.zip");

        assertEquals(HttpStatus.OK, success.getStatusCode());
        verify(backupService).delete("backup.zip");
        verify(auditLogService).log("BACKUP_DELETED", "Database", "backup.zip", null);

        doThrow(new IllegalStateException("locked")).when(backupService).delete("locked.zip");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, controller.delete("locked.zip").getStatusCode());
    }

    @Test
    void configurationExposesValuesAndValidatesRetention() {
        when(backupService.getBackupPath()).thenReturn("D:/backups");
        when(backupService.getRetention()).thenReturn(10);

        Map<String, Object> config = controller.getConfig().getBody();
        assertEquals("D:/backups", config.get("backupPath"));
        assertEquals(10, config.get("retention"));

        assertEquals(HttpStatus.BAD_REQUEST, controller.updateConfig(Map.of("retention", 0)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.updateConfig(Map.of("retention", 101)).getStatusCode());
        verify(backupService, never()).setRetention(0);

        assertEquals(HttpStatus.OK, controller.updateConfig(Map.of("retention", 12)).getStatusCode());
        verify(backupService).setRetention(12);
        verify(auditLogService).log("BACKUP_CONFIG_UPDATED", "System", "backup", "retention=12");

        assertEquals(HttpStatus.OK, controller.updateConfig(Map.of()).getStatusCode());
    }

    private BackupDto backup() {
        return BackupDto.builder()
                .name("backup.zip")
                .sizeBytes(2_048)
                .type("COMPLETE_APP")
                .build();
    }
}
