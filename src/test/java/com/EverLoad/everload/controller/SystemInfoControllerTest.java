package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.BackupDto;
import com.EverLoad.everload.dto.SystemInfoDto;
import com.EverLoad.everload.dto.UpdateCheckDto;
import com.EverLoad.everload.service.AuditLogService;
import com.EverLoad.everload.service.BackupService;
import com.EverLoad.everload.service.MaintenanceService;
import com.EverLoad.everload.service.NotificationService;
import com.EverLoad.everload.service.SystemInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemInfoControllerTest {

    private SystemInfoService systemInfoService;
    private BackupService backupService;
    private MaintenanceService maintenanceService;
    private AuditLogService auditLogService;
    private NotificationService notificationService;
    private SystemInfoController controller;

    @BeforeEach
    void setUp() {
        systemInfoService = mock(SystemInfoService.class);
        backupService = mock(BackupService.class);
        maintenanceService = mock(MaintenanceService.class);
        auditLogService = mock(AuditLogService.class);
        notificationService = mock(NotificationService.class);
        controller = new SystemInfoController(
                systemInfoService, backupService, maintenanceService, auditLogService, notificationService);
        ReflectionTestUtils.setField(controller, "updateScript", "");
    }

    @Test
    void infoAndUpdateCheckReturnServiceValues() {
        SystemInfoDto info = SystemInfoDto.builder().appVersion("2.0").build();
        UpdateCheckDto update = UpdateCheckDto.builder().latestVersion("2.1").build();
        when(systemInfoService.getInfo()).thenReturn(info);
        when(systemInfoService.checkUpdate()).thenReturn(update);

        assertEquals(info, controller.info().getBody());
        assertEquals(update, controller.checkUpdate().getBody());
        verify(auditLogService).log("UPDATE_CHECK", "System", "update", "latestVersion=2.1");
    }

    @Test
    void warnMaintenanceBuildsPluralDefaultAndKeepsCustomMessage() {
        ResponseEntity<?> defaultResponse = controller.warnMaintenance(Map.of("minutes", 3));
        ResponseEntity<?> customResponse = controller.warnMaintenance(Map.of("minutes", 1, "message", "Ahora"));

        Map<?, ?> defaultBody = (Map<?, ?>) defaultResponse.getBody();
        Map<?, ?> customBody = (Map<?, ?>) customResponse.getBody();
        assertTrue(String.valueOf(defaultBody.get("message")).contains("3 minutos"));
        assertEquals("Ahora", customBody.get("message"));
        verify(notificationService).createForAllActiveUsers(
                "admin_notice", "⚠️ Mantenimiento próximo", "Ahora");
    }

    @Test
    void prepareUpdateCreatesBackupAndActivatesMaintenanceWhenNoScriptConfigured() throws Exception {
        when(backupService.createBackup()).thenReturn(BackupDto.builder().name("backup.zip").build());

        ResponseEntity<?> response = controller.prepareUpdate(Map.of("message", "Actualizando"));

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("backup.zip", body.get("backup"));
        assertEquals(true, body.get("maintenanceActive"));
        verify(maintenanceService).activate("Actualizando");
        verify(auditLogService).log("UPDATE_STARTED", "System", "update", "backup=backup.zip | maintenance=ON");
    }

    @Test
    void prepareUpdateHandlesSuccessfulAndFailedScriptExitCodes() throws Exception {
        when(backupService.createBackup()).thenReturn(BackupDto.builder().name("backup.zip").build());
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        ReflectionTestUtils.setField(controller, "updateScript", windows ? "cmd /c exit 0" : "sh -c true");

        ResponseEntity<?> successResponse = controller.prepareUpdate(Map.of());
        Map<?, ?> success = (Map<?, ?>) successResponse.getBody();

        assertEquals(true, success.get("success"));
        verify(maintenanceService).deactivate();
        verify(auditLogService).log(
                "UPDATE_COMPLETED", "System", "update", "script exitCode=0 | maintenance=OFF");

        ReflectionTestUtils.setField(controller, "updateScript", windows ? "cmd /c exit 7" : "sh -c false");
        int failedExitCode = windows ? 7 : 1;
        ResponseEntity<?> failedResponse = controller.prepareUpdate(Map.of());
        Map<?, ?> failed = (Map<?, ?>) failedResponse.getBody();

        assertEquals(false, failed.get("success"));
        verify(auditLogService).log(
                "UPDATE_FAILED", "System", "update",
                "script exitCode=" + failedExitCode + " | maintenance=ON (manual reset needed)");
    }

    @Test
    void prepareUpdateReportsScriptStartFailure() throws Exception {
        when(backupService.createBackup()).thenReturn(BackupDto.builder().name("backup.zip").build());
        ReflectionTestUtils.setField(controller, "updateScript", "definitely-missing-everload-update-command");

        ResponseEntity<?> response = controller.prepareUpdate(Map.of());
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals(500, response.getStatusCode().value());
        assertEquals(false, body.get("success"));
        verify(auditLogService).log(
                eq("UPDATE_FAILED"), eq("System"), eq("update"), startsWith("scriptError="));
    }

    @Test
    void prepareUpdatePreservesInterruptAndAuditsInterruptedScript() throws Exception {
        when(backupService.createBackup()).thenReturn(BackupDto.builder().name("backup.zip").build());
        ReflectionTestUtils.setField(controller, "updateScript",
                "java -cp target/test-classes " + SystemInfoControllerTest.class.getName() + "$SilentProcess");
        Thread testThread = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            try {
                new CountDownLatch(1).await(200, TimeUnit.MILLISECONDS);
                testThread.interrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        interrupter.start();
        ResponseEntity<?> response = controller.prepareUpdate(Map.of());
        boolean interrupted = Thread.currentThread().isInterrupted();
        Thread.interrupted();
        interrupter.join();

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(500, response.getStatusCode().value());
        assertEquals(false, body.get("success"));
        assertTrue(interrupted);
        verify(auditLogService).log("UPDATE_FAILED", "System", "update", "script interrupted");
    }

    public static final class SilentProcess {
        private SilentProcess() {
        }

        public static void main(String[] args) throws Exception {
            System.out.close();
            System.err.close();
            new CountDownLatch(1).await(1, TimeUnit.SECONDS);
        }
    }
}
