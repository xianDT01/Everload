package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.UpdateUserRequest;
import com.EverLoad.everload.dto.UserDto;
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
            return ResponseEntity.ok(userService.updateUser(id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Revocar acceso a un usuario (estado REJECTED)")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<?> revokeAccess(@PathVariable Long id) {
        try {
            userService.revokeAccess(id);
            return ResponseEntity.ok(Map.of("message", "Acceso revocado correctamente"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Eliminar usuario")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "Usuario eliminado"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}