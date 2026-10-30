package com.everload.everload.controller;

import com.everload.everload.dto.BackupDto;
import com.everload.everload.dto.SystemInfoDto;
import com.everload.everload.dto.UpdateCheckDto;
import com.everload.everload.service.AuditLogService;
import com.everload.everload.service.BackupService;
import com.everload.everload.service.MaintenanceService;
import com.everload.everload.service.NotificationService;
import com.everload.everload.service.SystemInfoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Tag(name = "System", description = "Información del sistema y gestión de actualizaciones")
@RestController
@RequestMapping("/api/admin/system")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SystemInfoController {

    private static final String UPDATE_FAILED_EVENT = "UPDATE_FAILED";
    private static final String AUDIT_SYSTEM = "System";
    private static final String AUDIT_UPDATE = "update";
    private static final String MESSAGE_KEY = "message";
    private static final String SUCCESS_KEY = "success";
    private static final String BACKUP_KEY = "backup";

    private final SystemInfoService systemInfoService;
    private final BackupService backupService;
    private final MaintenanceService maintenanceService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Value("${app.update.script:}")
    private String updateScript;
    private final AtomicBoolean preparing = new AtomicBoolean();

    // ── System info ────────────────────────────────────────────────────────────

    @GetMapping("/info")
    public ResponseEntity<SystemInfoDto> info() {
        return ResponseEntity.ok(systemInfoService.getInfo());
    }

    // ── Update check ───────────────────────────────────────────────────────────

    @GetMapping("/check-update")
    public ResponseEntity<UpdateCheckDto> checkUpdate() {
        UpdateCheckDto result = systemInfoService.checkUpdate();
        auditLogService.log("UPDATE_CHECK", AUDIT_SYSTEM, AUDIT_UPDATE,
                "latestVersion=" + result.getLatestVersion());
        return ResponseEntity.ok(result);
    }

    // ── Warn users before maintenance ─────────────────────────────────────────

    @PostMapping("/warn-maintenance")
    public ResponseEntity<Object> warnMaintenance(@RequestBody(required = false) Map<String, Object> body) {
        int minutes = 0;
        String customMsg = null;
        if (body != null) {
            if (body.containsKey("minutes")) {
                if (!(body.get("minutes") instanceof Number n) || n.doubleValue() != n.intValue()
                        || n.intValue() < 1 || n.intValue() > 1440) {
                    return ResponseEntity.badRequest().body(Map.of("error", "El plazo debe estar entre 1 y 1440 minutos"));
                }
                minutes = n.intValue();
            }
            if (body.get(MESSAGE_KEY) instanceof String s && !s.isBlank()) customMsg = s;
        }
        String minuteLabel = minutes == 1 ? "minuto" : "minutos";
        String defaultMessage = minutes > 0
                ? "El sistema entrara en mantenimiento en " + minutes + " " + minuteLabel + "."
                : "Se ha anunciado un proximo mantenimiento. Guarda los cambios pendientes.";
        String msg = customMsg != null ? customMsg : defaultMessage;
        notificationService.createForAllActiveUsers("admin_notice", "⚠️ Mantenimiento próximo", msg);
        auditLogService.log("MAINTENANCE_WARNING", AUDIT_SYSTEM, "maintenance",
                "notified all active users | minutes=" + minutes);
        return ResponseEntity.ok(Map.of("ok", true, MESSAGE_KEY, msg));
    }

    // ── Prepare update ─────────────────────────────────────────────────────────

    /**
     * Pre-update routine:
     * <ol>
     *   <li>Activate maintenance mode</li>
     *   <li>Create an automatic backup; restore the prior mode if it fails</li>
     *   <li>Optionally run the configured update script</li>
     * </ol>
     *
     * <p>Body: {@code { "message": "Actualizando la app..." }}
     */
    @PostMapping("/prepare-update")
    public ResponseEntity<Object> prepareUpdate(@RequestBody(required = false) Map<String, String> body) {
        if (!preparing.compareAndSet(false, true)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya hay una preparacion en curso"));
        }
        try {
            return prepareUpdateInternal(body);
        } finally {
            preparing.set(false);
        }
    }

    private ResponseEntity<Object> prepareUpdateInternal(Map<String, String> body) {
        String maintenanceMsg = (body != null && body.containsKey(MESSAGE_KEY))
                ? body.get(MESSAGE_KEY)
                : "La aplicación está actualizándose. Vuelve en unos minutos.";

        boolean wasActive = maintenanceService.isActive();
        String previousMessage = maintenanceService.getMessage();
        maintenanceService.activate(maintenanceMsg);
        // Block new user writes before taking the pre-update snapshot.
        String backupName = null;
        try {
            BackupDto backup = backupService.createBackup();
            backupName = backup.getName();
            log.info("[UPDATE] Pre-update backup created: {}", backupName);
        } catch (Exception e) {
            log.error("[UPDATE] Pre-update backup failed: {}", e.getMessage());
            if (wasActive) maintenanceService.activate(previousMessage);
            else maintenanceService.deactivate();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "No se pudo crear la copia de seguridad previa. No se ha preparado la actualizacion."));
        }

        auditLogService.log("UPDATE_STARTED", AUDIT_SYSTEM, AUDIT_UPDATE,
                "backup=" + backupName + " | maintenance=ON");

        // Step 3 — Run update script (optional)
        if (updateScript != null && !updateScript.isBlank()) {
            Process process = null;
            try {
                log.info("[UPDATE] Running update script: {}", updateScript);
                ProcessBuilder pb = new ProcessBuilder(updateScript.split("\\s+"));
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                process = pb.start();
                if (!process.waitFor(120, TimeUnit.SECONDS)) {
                    throw new java.io.IOException("El script ha superado el limite de 120 segundos");
                }
                int exitCode = process.exitValue();

                if (exitCode == 0) {
                    maintenanceService.deactivate();
                    auditLogService.log("UPDATE_COMPLETED", AUDIT_SYSTEM, AUDIT_UPDATE,
                            "script exitCode=0 | maintenance=OFF");
                    return ResponseEntity.ok(Map.of(
                            SUCCESS_KEY, true,
                            BACKUP_KEY, backupName,
                            MESSAGE_KEY, "✅ Actualización completada. Mantenimiento desactivado."));
                } else {
                    auditLogService.log(UPDATE_FAILED_EVENT, AUDIT_SYSTEM, AUDIT_UPDATE,
                            "script exitCode=" + exitCode + " | maintenance=ON (manual reset needed)");
                    return ResponseEntity.ok(Map.of(
                            SUCCESS_KEY, false,
                            BACKUP_KEY, backupName,
                            MESSAGE_KEY, "⚠️ El script de actualización falló (código " + exitCode + "). " +
                                       "El modo mantenimiento sigue activo. Revisa los logs y desactívalo manualmente."));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[UPDATE] Script execution interrupted");
                auditLogService.log(UPDATE_FAILED_EVENT, AUDIT_SYSTEM, AUDIT_UPDATE, "script interrupted");
                return ResponseEntity.internalServerError()
                        .body(Map.of(
                                SUCCESS_KEY, false,
                                BACKUP_KEY, backupName,
                                MESSAGE_KEY, "La actualizacion fue interrumpida. El mantenimiento sigue activo."));
            } catch (Exception e) {
                log.error("[UPDATE] Script execution failed: {}", e.getMessage());
                auditLogService.log(UPDATE_FAILED_EVENT, AUDIT_SYSTEM, AUDIT_UPDATE,
                        "scriptError=" + e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of(
                                SUCCESS_KEY, false,
                                BACKUP_KEY, backupName,
                                MESSAGE_KEY, "❌ Error al ejecutar el script: " + e.getMessage()
                                         + ". El mantenimiento sigue activo."));
            } finally {
                if (process != null && process.isAlive()) process.destroyForcibly();
            }
        }

        // No script configured — return instructions for manual update
        return ResponseEntity.ok(Map.of(
                SUCCESS_KEY, true,
                "preparedOnly", true,
                BACKUP_KEY, backupName,
                "maintenanceActive", true,
                MESSAGE_KEY, "✅ Copia de seguridad creada y modo mantenimiento activado. " +
                           "Realiza el despliegue desde el servidor, comprueba la salud del contenedor " +
                           "y desactiva el mantenimiento desde el panel cuando termines."));
    }
}
