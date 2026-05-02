package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.DownloadService;
import com.EverLoad.everload.service.DownloadService.DirectDownloadJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Tag(name = "Descargas", description = "Descargas desde varias plataformas")
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @Operation(summary = "Descargar un vídeo")
    @GetMapping("/downloadVideo")
    public ResponseEntity<FileSystemResource> downloadVideo(
            @RequestParam String videoId,
            @RequestParam String resolution) {
        return downloadService.downloadVideo(videoId, resolution);
    }

    @Operation(summary = "Descargar música")
    @GetMapping("/downloadMusic")
    public ResponseEntity<FileSystemResource> downloadMusic(
            @RequestParam String videoId,
            @RequestParam String format) {
        return downloadService.downloadMusic(videoId, format);
    }

    @Operation(summary = "Encolar descarga de música de YouTube")
    @PostMapping("/downloadMusic/jobs")
    public ResponseEntity<?> queueMusicDownload(
            @RequestParam String videoId,
            @RequestParam(defaultValue = "mp3") String format) {
        try {
            return ResponseEntity.ok(downloadService.queueMusicDownload(videoId, format));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Consultar estado de descarga de música")
    @GetMapping("/downloadMusic/jobs/{jobId}")
    public ResponseEntity<DirectDownloadJob> getMusicDownloadJob(@PathVariable String jobId) {
        DirectDownloadJob job = downloadService.getDirectDownloadJob(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(job);
    }

    @Operation(summary = "Descargar archivo de música preparado")
    @GetMapping("/downloadMusic/jobs/{jobId}/file")
    public ResponseEntity<FileSystemResource> downloadQueuedMusicFile(@PathVariable String jobId) {
        return downloadService.downloadQueuedFile(jobId);
    }

    @Operation(summary = "Descargar video de Twitter/X")
    @GetMapping("/downloadTwitter")
    public ResponseEntity<FileSystemResource> downloadTwitterVideo(
            @RequestParam String url) {
        return downloadService.downloadTwitterVideo(url);
    }

    @Operation(summary = "Descargar video de Facebook")
    @GetMapping("/downloadFacebook")
    public ResponseEntity<FileSystemResource> downloadFacebookVideo(
            @RequestParam String url) {
        return downloadService.downloadFacebookVideo(url);
    }

    @Operation(summary = "Descargar video de Instagram")
    @GetMapping("/downloadInstagram")
    public ResponseEntity<FileSystemResource> downloadInstagramVideo(
            @RequestParam String url) {
        return downloadService.downloadInstagramVideo(url);
    }
    @Operation(summary = "Obtener vídeos de una playlist de YouTube")
    @GetMapping("/playlistVideos")
    public ResponseEntity<?> getPlaylistVideos(@RequestParam String playlistUrl) {
        return downloadService.getPlaylistVideos(playlistUrl);
    }
    @Operation(summary = "Descargar video de TikTok")
    @GetMapping("/downloadTikTok")
    public ResponseEntity<FileSystemResource> downloadTikTokVideo(@RequestParam String url) {
        return downloadService.downloadTikTokVideo(url);
    }

    @Operation(summary = "Guardar música directamente en el NAS (sin descarga al navegador)")
    @PostMapping("/saveMusicToNas")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER')")
    public ResponseEntity<Map<String, String>> saveMusicToNas(
            @RequestParam String videoId,
            @RequestParam(defaultValue = "mp3") String format,
            @RequestParam Long nasPathId,
            @RequestParam(required = false, defaultValue = "") String subPath) {
        try {
            Map<String, String> result = downloadService.saveMusicToNas(videoId, format, nasPathId, subPath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

}
