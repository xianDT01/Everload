package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.NasYtDlpService;
import com.EverLoad.everload.service.NasYtDlpService.YtDlpJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "NAS yt-dlp", description = "Descarga asíncrona de YouTube al NAS con metadatos")
@RestController
@RequestMapping("/api/nas/ytdlp")
@PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
public class NasYtDlpController {

    private final NasYtDlpService service;

    public NasYtDlpController(NasYtDlpService service) {
        this.service = service;
    }

    @Operation(summary = "Encolar descarga de YouTube al NAS (asíncrono)")
    @PostMapping("/queue")
    public ResponseEntity<Map<String, String>> queue(
            @RequestParam String videoId,
            @RequestParam(required = false, defaultValue = "") String title,
            @RequestParam Long nasPathId,
            @RequestParam(required = false, defaultValue = "") String subPath,
            @RequestParam(required = false, defaultValue = "mp3") String format) {
        String jobId = service.queue(videoId, title, nasPathId, subPath, format);
        return ResponseEntity.ok(Map.of("jobId", jobId));
    }

    @Operation(summary = "Estado de un job")
    @GetMapping("/status/{jobId}")
    public ResponseEntity<YtDlpJob> getStatus(@PathVariable String jobId) {
        YtDlpJob job = service.getJob(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(job);
    }

    @Operation(summary = "Jobs activos/recientes (última hora)")
    @GetMapping("/active")
    public ResponseEntity<List<YtDlpJob>> getActive() {
        return ResponseEntity.ok(service.getActiveJobs());
    }
}
