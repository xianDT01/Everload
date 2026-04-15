package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.MaintenanceStatusDto;
import com.EverLoad.everload.service.AuditLogService;
import com.EverLoad.everload.service.MaintenanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Maintenance", description = "Control del modo mantenimiento de la aplicación")
@RestController
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;
    private final AuditLogService auditLogService;

    /** Public endpoint — Angular app polls this to show maintenance screen to non-admin users. */
    @GetMapping("/api/maintenance/status")
    public ResponseEntity<MaintenanceStatusDto> publicStatus() {
        return ResponseEntity.ok(maintenanceService.getStatus());
    }

    /** Admin: get full maintenance status. */
    @GetMapping("/api/admin/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MaintenanceStatusDto> getStatus() {
        return ResponseEntity.ok(maintenanceService.getStatus());
    }

    /**
     * Admin: activate or deactivate maintenance mode.
     *
     * <p>Body: {@code { "active": true, "message": "Optional custom message" }}
     */
    @PutMapping("/api/admin/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MaintenanceStatusDto> setStatus(@RequestBody Map<String, Object> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        String message = body.get("message") instanceof String s ? s : null;

        if (active) {
            maintenanceService.activate(message);
            auditLogService.log("MAINTENANCE_ENABLED", "System", "maintenance",
                    "Mensaje: " + maintenanceService.getMessage());
        } else {
            maintenanceService.deactivate();
            auditLogService.log("MAINTENANCE_DISABLED", "System", "maintenance", null);
        }

        return ResponseEntity.ok(maintenanceService.getStatus());
    }
}