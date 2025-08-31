package com.EverLoad.everload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@RestController
@RequestMapping("/api/admin/logs")
public class LogController {

    private static final String LOG_PATH = "everload.log";

    @GetMapping
    public ResponseEntity<List<String>> getLogs(
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(required = false) String filter) {
        try {
            Path path = Path.of(LOG_PATH);
            if (!Files.exists(path)) {
                return ResponseEntity.status(404).body(List.of("Log file not found"));
            }

            List<String> allLines = Files.readAllLines(path);
            List<String> filtered = allLines.stream()
                    .filter(line -> filter == null || line.toLowerCase().contains(filter.toLowerCase()))
                    .skip(Math.max(0, allLines.size() - lines))
                    .toList();

            return ResponseEntity.ok(filtered);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(List.of("❌ Error reading log: " + e.getMessage()));
        }
    }

    @PostMapping("/clear")
    public ResponseEntity<String> clearLog() {
        try {
            Path path = Path.of(LOG_PATH);
            if (Files.exists(path)) {
                Files.write(path, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
                return ResponseEntity.ok("🧹 Log cleared successfully.");
            } else {
                return ResponseEntity.status(404).body("⚠️ Log file not found.");
            }
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("❌ Error clearing log: " + e.getMessage());
        }
    }

    // Every 7 days (7 * 24 * 60 * 60 * 1000 ms)
    @Scheduled(fixedRate = 604_800_000)
    public void autoClearLog() {
        try {
            Path path = Path.of(LOG_PATH);
            if (Files.exists(path)) {
                Files.write(path, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("🧹 Log automatically cleared.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}