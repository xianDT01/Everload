package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.AuthResponse;
import com.EverLoad.everload.dto.LoginRequest;
import com.EverLoad.everload.dto.RegisterRequest;
import com.EverLoad.everload.security.JwtUtil;
import com.EverLoad.everload.service.AuthService;
import com.EverLoad.everload.service.TokenRevocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Autenticación", description = "Registro e inicio de sesión de usuarios")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final TokenRevocationService tokenRevocationService;

    @Operation(summary = "Registrar nuevo usuario")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Iniciar sesión")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Cerrar sesión — invalida el token actual")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                String jti = jwtUtil.extractJti(token);
                java.time.Instant expiresAt = jwtUtil.extractExpiration(token).toInstant();
                tokenRevocationService.revoke(jti, expiresAt);
            } catch (Exception ignored) {
                // Invalid token — logout is idempotent, just return OK
            }
        }
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
    }
}