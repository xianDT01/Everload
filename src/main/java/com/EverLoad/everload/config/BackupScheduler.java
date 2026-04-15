package com.EverLoad.everload.config;

import com.EverLoad.everload.service.AuditLogService;
import com.EverLoad.everload.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Automatic periodic backup scheduler.
 *
 * <p>Cron defaults to {@code 0 0 3 * * ?} (3 AM every day).
 * Set {@code app.backup.auto.enabled=false} to disable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;
    private final AuditLogService auditLogService;

    @Value("${app.backup.auto.enabled:false}")
    private boolean autoBackupEnabled;

    /** Default: 3 AM every day. Override via {@code app.backup.auto.cron}. */
    @Scheduled(cron = "${app.backup.auto.cron:0 0 3 * * ?}")
    public void autoBackup() {
        if (!autoBackupEnabled) return;

        try {
            var dto = backupService.createBackup();
            auditLogService.log("BACKUP_AUTO", "Database", dto.getName(),
                    "Copia automática programada. Tamaño: " + dto.getSizeFormatted());
            log.info("[BACKUP] Auto-backup created: {} ({})", dto.getName(), dto.getSizeFormatted());
        } catch (Exception e) {
            log.error("[BACKUP] Auto-backup failed: {}", e.getMessage());
        }
    }
}