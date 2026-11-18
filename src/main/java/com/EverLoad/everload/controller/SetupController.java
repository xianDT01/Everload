package com.everload.everload.controller;

import com.everload.everload.dto.SetupAdminRequest;
import com.everload.everload.dto.AuthResponse;
import com.everload.everload.service.AuthService;
import com.everload.everload.service.SetupService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {
    private final SetupService setup;
    private final AuthService auth;
    private final jakarta.validation.Validator validator;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("required", setup.required()));
    }

    @PostMapping("/admin")
    public ResponseEntity<?> create(@RequestBody SetupAdminRequest request) {
        // Do not throw a binding exception containing rejected passwords: DEBUG MVC logs would print it.
        if (!validator.validate(request).isEmpty()) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error", "SETUP_FIELDS"));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.refreshToken(setup.createAdmin(request)));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> failure(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("error", error.getReason() == null ? "SETUP_FAILED" : error.getReason()));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error", "SETUP_FIELDS"));
    }
}
