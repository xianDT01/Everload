package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.DownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "Controlador de Descargas", description = "API para descargar vídeos e música de YouTube")
@RestController
public class DownloadController {

    @Autowired
    private DownloadService downloadService;

    @Operation(summary = "Descargar un vídeo", description = "Descarga un vídeo na resolución especificada usando yt-dlp.")
    @GetMapping("/downloadVideo")
    public String downloadVideo(
            @Parameter(description = "ID do vídeo de YouTube") @RequestParam String videoId,
            @Parameter(description = "Resolución desexada do vídeo, por exemplo: 720, 1080") @RequestParam String resolution) {
        downloadService.downloadVideo(videoId, resolution);
        return "Descargando vídeo: " + videoId + " con resolución: " + resolution;
    }

    @Operation(summary = "Descargar música", description = "Extrae e descarga o audio dun vídeo de YouTube no formato especificado.")
    @GetMapping("/downloadMusic")
    public String downloadMusic(
            @Parameter(description = "ID do vídeo de YouTube") @RequestParam String videoId,
            @Parameter(description = "Formato de audio desexado, por exemplo: mp3, wav") @RequestParam String format) {
        downloadService.downloadMusic(videoId, format);
        return "Descargando música: " + videoId + " no formato: " + format;
    }
}
