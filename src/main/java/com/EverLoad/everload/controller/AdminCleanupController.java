package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.CleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/cleanup")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCleanupController {

    private final CleanupService cleanupService;

    @PostMapping("/metadata-cache")
    public ResponseEntity<Map<String, Object>> purgeMetadataCache() {
        return ResponseEntity.ok(cleanupService.purgeOrphanedMetadataCache());
    }

    @PostMapping("/expired-tokens")
    public ResponseEntity<Map<String, Object>> purgeExpiredTokens() {
        return ResponseEntity.ok(cleanupService.purgeExpiredTokens());
    }

    @PostMapping("/playback-history")
    public ResponseEntity<Map<String, Object>> trimPlaybackHistory(
            @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(cleanupService.trimPlaybackHistory(days));
    }

    @PostMapping("/audit-logs")
    public ResponseEntity<Map<String, Object>> trimAuditLogs(
            @RequestParam(defaultValue = "90") int days) {
        return ResponseEntity.ok(cleanupService.trimAuditLogs(days));
    }
}
