package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.BackupDto;
import com.EverLoad.everload.dto.SystemInfoDto;
import com.EverLoad.everload.dto.UpdateCheckDto;
import com.EverLoad.everload.service.AuditLogService;
import com.EverLoad.everload.service.BackupService;
import com.EverLoad.everload.service.MaintenanceService;
import com.EverLoad.everload.service.NotificationService;
import com.EverLoad.everload.service.SystemInfoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "System", description = "Información del sistema y gestión de actualizaciones")
@RestController
@RequestMapping("/api/admin/system")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SystemInfoController {

    private final SystemInfoService systemInfoService;
    private final BackupService backupService;
    private final MaintenanceService maintenanceService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Value("${app.update.script:}")
    private String updateScript;

    // ── System info ────────────────────────────────────────────────────────────

    @GetMapping("/info")
    public ResponseEntity<SystemInfoDto> info() {
        return ResponseEntity.ok(systemInfoService.getInfo());
    }

    // ── Update check ───────────────────────────────────────────────────────────

    @GetMapping("/check-update")
    public ResponseEntity<UpdateCheckDto> checkUpdate() {
        UpdateCheckDto result = systemInfoService.checkUpdate();
        auditLogService.log("UPDATE_CHECK", "System", "update",
                "latestVersion=" + result.getLatestVersion());
        return ResponseEntity.ok(result);
    }

    // ── Warn users before maintenance ─────────────────────────────────────────

    @PostMapping("/warn-maintenance")
    public ResponseEntity<?> warnMaintenance(@RequestBody(required = false) Map<String, Object> body) {
        int minutes = 1;
        String customMsg = null;
        if (body != null) {
            if (body.get("minutes") instanceof Number n) minutes = n.intValue();
            if (body.get("message") instanceof String s && !s.isBlank()) customMsg = s;
        }
        String msg = customMsg != null ? customMsg
                : "⚠️ El sistema entrará en mantenimiento en " + minutes + " minuto" + (minutes == 1 ? "" : "s") + " para realizar una actualización.";
        notificationService.createForAllActiveUsers("admin_notice", "⚠️ Mantenimiento próximo", msg);
        auditLogService.log("MAINTENANCE_WARNING", "System", "maintenance",
                "notified all active users | minutes=" + minutes);
        return ResponseEntity.ok(Map.of("ok", true, "message", msg));
    }

    // ── Prepare update ─────────────────────────────────────────────────────────

    /**
     * Pre-update routine:
     * <ol>
     *   <li>Create an automatic backup</li>
     *   <li>Activate maintenance mode</li>
     *   <li>Optionally run the configured update script</li>
     * </ol>
     *
     * <p>Body: {@code { "message": "Actualizando la app..." }}
     */
    @PostMapping("/prepare-update")
    public ResponseEntity<?> prepareUpdate(@RequestBody(required = false) Map<String, String> body) {
        String maintenanceMsg = (body != null && body.containsKey("message"))
                ? body.get("message")
                : "La aplicación está actualizándose. Vuelve en unos minutos.";

        // Step 1 — Auto backup
        String backupName = null;
        try {
            BackupDto backup = backupService.createBackup();
            backupName = backup.getName();
            log.info("[UPDATE] Pre-update backup created: {}", backupName);
        } catch (Exception e) {
            log.error("[UPDATE] Pre-update backup failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "No se pudo crear la copia de seguridad previa: "
                                          + e.getMessage()));
        }

        // Step 2 — Activate maintenance
        maintenanceService.activate(maintenanceMsg);
        auditLogService.log("UPDATE_STARTED", "System", "update",
                "backup=" + backupName + " | maintenance=ON");

        // Step 3 — Run update script (optional)
        if (updateScript != null && !updateScript.isBlank()) {
            try {
                log.info("[UPDATE] Running update script: {}", updateScript);
                ProcessBuilder pb = new ProcessBuilder(updateScript.split("\\s+"));
                pb.redirectErrorStream(true);
                Process process = pb.start();
                String output = new String(process.getInputStream().readAllBytes());
                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    maintenanceService.deactivate();
                    auditLogService.log("UPDATE_COMPLETED", "System", "update",
                            "script exitCode=0 | maintenance=OFF");
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "backup", backupName,
                            "scriptOutput", output,
                            "message", "✅ Actualización completada. Mantenimiento desactivado."));
                } else {
                    auditLogService.log("UPDATE_FAILED", "System", "update",
                            "script exitCode=" + exitCode + " | maintenance=ON (manual reset needed)");
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "backup", backupName,
                            "scriptOutput", output,
                            "message", "⚠️ El script de actualización falló (código " + exitCode + "). " +
                                       "El modo mantenimiento sigue activo. Revisa los logs y desactívalo manualmente."));
                }
            } catch (Exception e) {
                log.error("[UPDATE] Script execution failed: {}", e.getMessage());
                auditLogService.log("UPDATE_FAILED", "System", "update",
                        "scriptError=" + e.getMessage());
                return ResponseEntity.internalServerError()
                        .body(Map.of(
                                "success", false,
                                "backup", backupName,
                                "message", "❌ Error al ejecutar el script: " + e.getMessage()
                                         + ". El mantenimiento sigue activo."));
            }
        }

        // No script configured — return instructions for manual update
        return ResponseEntity.ok(Map.of(
                "success", true,
                "backup", backupName,
                "maintenanceActive", true,
                "message", "✅ Copia de seguridad creada y modo mantenimiento activado. " +
                           "Realiza la actualización manualmente (p. ej. docker pull + restart) " +
                           "y desactiva el mantenimiento desde el panel cuando termines."));
    }
}