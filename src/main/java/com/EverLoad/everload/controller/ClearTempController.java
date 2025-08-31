package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.DownloadHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class ClearTempController {

    private final DownloadHistoryService history;

    public ClearTempController(DownloadHistoryService history) {
        this.history = history;
    }

    @Operation(summary = "Delete temporary download folders")
    @GetMapping({"/clear-temp", "/limpiarTemp"}) // inglés + compatibilidad
    public ResponseEntity<String> clearTemp() {
        boolean ok = history.clearTemporaryFolders();
        return ok
                ? ResponseEntity.ok("🧹 Temporary folders removed.")
                : ResponseEntity.status(500).body("❌ Error removing temporary folders.");
    }
}