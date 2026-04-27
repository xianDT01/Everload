package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.AuditLog;
import com.EverLoad.everload.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * GET /api/admin/audit?page=0&size=50&search=keyword
     * Returns a paginated list of admin actions, newest first.
     */
    @GetMapping
    public ResponseEntity<Page<AuditLog>> getLogs(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "50")  int size,
            @RequestParam(required = false)     String search) {
        return ResponseEntity.ok(auditLogService.getPage(page, Math.min(size, 200), search));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Long>> clearLogs() {
        long deleted = auditLogService.clearAll();
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
