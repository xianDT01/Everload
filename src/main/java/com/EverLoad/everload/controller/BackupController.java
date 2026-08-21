package com.everload.everload.controller;

import com.everload.everload.dto.BackupDto;
import com.everload.everload.service.AuditLogService;
import com.everload.everload.service.BackupService;
import com.everload.everload.service.BackupService.BackupType;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "Backup", description = "Gestión de copias de seguridad de la base de datos H2")
@RestController
@RequestMapping("/api/admin/backup")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BackupController {

    private static final String DATABASE_COMPONENT = "Database";
    private static final String ERROR_FIELD = "error";
    private static final String SUCCESS_FIELD = "success";
    private static final String RETENTION_FIELD = "retention";

    private final BackupService backupService;
    private final AuditLogService auditLogService;

    // ── List ───────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<BackupDto>> list() {
        try {
            return ResponseEntity.ok(backupService.listBackups());
        } catch (Exception e) {
            log.error("[BACKUP] List failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Create ─────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) Map<String, String> body) {
        try {
            BackupType type = parseBackupType(body != null ? body.get("type") : null);
            BackupDto dto = backupService.createBackup(type);
            auditLogService.log("BACKUP_CREATED", DATABASE_COMPONENT, dto.getName(),
                    "Tipo: " + dto.getType() + " | Tamano: " + dto.getSizeFormatted());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("[BACKUP] Create failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of(ERROR_FIELD, "Error al crear la copia: " + e.getMessage()));
        }
    }

    // ── Restore ────────────────────────────────────────────────────────────────

    /**
     * Restores the database from the named backup.
     * The caller is responsible for activating maintenance mode beforehand.
     *
     * <p>Body: {@code { "filename": "backup_2024-01-15_14-30-00.zip" }}
     */
    @PostMapping("/restore")
    public ResponseEntity<Object> restore(@RequestBody Map<String, String> body) {
        String filename = body.get("filename");
        if (filename == null || filename.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_FIELD, "filename requerido"));
        }
        try {
            backupService.restore(filename);
            auditLogService.log("BACKUP_RESTORED", DATABASE_COMPONENT, filename,
                    "Copia restaurada correctamente");
            log.warn("[BACKUP] Database restored from {}. Sessions may be stale — advise re-login.", filename);
            return ResponseEntity.ok(Map.of(
                    SUCCESS_FIELD, true,
                    "message", "Copia restaurada correctamente desde '" + filename + "'. " +
                               "Por seguridad, cierra sesión y vuelve a iniciar."));
        } catch (Exception e) {
            log.error("[BACKUP] Restore failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of(ERROR_FIELD, "Error al restaurar: " + e.getMessage()));
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    @DeleteMapping("/{filename}")
    public ResponseEntity<Object> delete(@PathVariable String filename) {
        try {
            backupService.delete(filename);
            auditLogService.log("BACKUP_DELETED", DATABASE_COMPONENT, filename, null);
            return ResponseEntity.ok(Map.of(SUCCESS_FIELD, true));
        } catch (Exception e) {
            log.error("[BACKUP] Delete failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of(ERROR_FIELD, "Error al eliminar: " + e.getMessage()));
        }
    }

    // ── Config ─────────────────────────────────────────────────────────────────

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "backupPath", backupService.getBackupPath(),
                RETENTION_FIELD,  backupService.getRetention()
        ));
    }

    /** Update retention count (backup path requires a server restart to change). */
    @PutMapping("/config")
    public ResponseEntity<Object> updateConfig(@RequestBody Map<String, Object> body) {
        if (body.containsKey(RETENTION_FIELD)) {
            int r = ((Number) body.get(RETENTION_FIELD)).intValue();
            if (r < 1 || r > 100) {
                return ResponseEntity.badRequest()
                        .body(Map.of(ERROR_FIELD, "retention debe estar entre 1 y 100"));
            }
            backupService.setRetention(r);
            auditLogService.log("BACKUP_CONFIG_UPDATED", "System", "backup",
                    "retention=" + r);
        }
        return ResponseEntity.ok(Map.of(SUCCESS_FIELD, true));
    }

    private BackupType parseBackupType(String type) {
        if (type == null || type.isBlank()) return BackupType.QUICK;
        try {
            return BackupType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return BackupType.QUICK;
        }
    }
}
