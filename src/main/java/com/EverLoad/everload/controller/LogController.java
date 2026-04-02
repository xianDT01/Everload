package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping
    public ResponseEntity<List<String>> getLogs(
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(required = false) String filter) {
        try {
            return ResponseEntity.ok(logService.getLines(lines, filter));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(List.of("❌ Error reading log: " + e.getMessage()));
        }
    }

    @PostMapping("/clear")
    public ResponseEntity<String> clearLog() {
        try {
            boolean found = logService.clearLog();
            return found
                    ? ResponseEntity.ok("🧹 Log cleared successfully.")
                    : ResponseEntity.status(404).body("⚠️ Log file not found.");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("❌ Error clearing log: " + e.getMessage());
        }
    }
}
