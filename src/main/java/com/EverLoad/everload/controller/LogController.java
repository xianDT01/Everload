package com.EverLoad.everload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/admin/logs")
public class LogController {

    private final String LOG_PATH = "everload.log";

    @GetMapping
    public ResponseEntity<List<String>> getLogs(
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(required = false) String filter) {
        try {
            Path path = Path.of(LOG_PATH);
            if (!Files.exists(path)) {
                return ResponseEntity.status(404).body(List.of("Log no encontrado"));
            }

            List<String> allLines = Files.readAllLines(path);
            List<String> filtered = allLines.stream()
                    .filter(line -> filter == null || line.toLowerCase().contains(filter.toLowerCase()))
                    .skip(Math.max(0, allLines.size() - lines)) // últimas N líneas
                    .toList();

            return ResponseEntity.ok(filtered);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(List.of("❌ Error leyendo el log: " + e.getMessage()));
        }
    }
}