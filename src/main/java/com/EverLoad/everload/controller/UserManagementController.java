package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.UpdateUserRequest;
import com.EverLoad.everload.dto.UserDto;
import com.EverLoad.everload.service.AuditLogService;
import com.EverLoad.everload.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Gestión de Usuarios", description = "Panel de administración de usuarios (solo ADMIN)")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    @Operation(summary = "Listar todos los usuarios")
    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Listar usuarios pendientes de aprobación")
    @GetMapping("/pending")
    public ResponseEntity<List<UserDto>> getPendingUsers() {
        return ResponseEntity.ok(userService.getPendingUsers());
    }

    @Operation(summary = "Listar usuarios activos")
    @GetMapping("/active")
    public ResponseEntity<List<UserDto>> getActiveUsers() {
        return ResponseEntity.ok(userService.getActiveUsers());
    }

    @Operation(summary = "Actualizar rol y/o estado de un usuario")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        try {
            UserDto updated = userService.updateUser(id, request);
            String action = deriveUpdateAction(request);
            String detail = buildUpdateDetail(request);
            auditLogService.log(action, "User", updated.getUsername(), detail);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Revocar acceso a un usuario (estado REJECTED)")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<?> revokeAccess(@PathVariable Long id) {
        try {
            String username = userService.getUsernameById(id);
            userService.revokeAccess(id);
            auditLogService.log("USER_REVOKED", "User", username, null);
            return ResponseEntity.ok(Map.of("message", "Acceso revocado correctamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Eliminar usuario")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            String username = userService.getUsernameById(id);
            userService.deleteUser(id);
            auditLogService.log("USER_DELETED", "User", username, null);
            return ResponseEntity.ok(Map.of("message", "Usuario eliminado"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String deriveUpdateAction(UpdateUserRequest req) {
        if (req.getStatus() != null) {
            return switch (req.getStatus().name()) {
                case "ACTIVE"    -> "USER_APPROVED";
                case "REJECTED"  -> "USER_REJECTED";
                default          -> "USER_STATUS_CHANGED";
            };
        }
        return "USER_ROLE_CHANGED";
    }

    private String buildUpdateDetail(UpdateUserRequest req) {
        if (req.getRole() != null && req.getStatus() != null)
            return "role=" + req.getRole() + ", status=" + req.getStatus();
        if (req.getRole() != null) return "role=" + req.getRole();
        if (req.getStatus() != null) return "status=" + req.getStatus();
        return null;
    }
}