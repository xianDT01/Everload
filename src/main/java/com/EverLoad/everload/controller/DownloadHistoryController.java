package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.Download;
import com.EverLoad.everload.service.DownloadHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
// Soporta ambas rutas: nueva en inglés y la existente en español
@RequestMapping({"/api/admin/history", "/api/admin/historial"})
public class DownloadHistoryController {

    private final DownloadHistoryService history;

    public DownloadHistoryController(DownloadHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public ResponseEntity<List<Download>> getHistory() {
        return ResponseEntity.ok(history.getHistory());
    }

    @DeleteMapping("/clear")
    public ResponseEntity<String> clearHistory() {
        try {
            history.clearHistory();
            return ResponseEntity.ok("🗑️ History cleared successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Error clearing history.");
        }
    }

    // Alias de compatibilidad con el endpoint antiguo
    @DeleteMapping("/vaciar")
    public ResponseEntity<String> clearHistoryLegacy() {
        return clearHistory();
    }
}