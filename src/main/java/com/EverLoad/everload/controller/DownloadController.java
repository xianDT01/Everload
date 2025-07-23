package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.DownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
@Tag(name = "Descargas", description = "Descargas desde varias plataformas")
@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = "http://localhost:4200",
        exposedHeaders = "Content-Disposition"
)
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


}