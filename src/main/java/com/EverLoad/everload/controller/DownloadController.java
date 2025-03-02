package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.DownloadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DownloadController {
    @Autowired
    private DownloadService downloadService;

    @GetMapping("/downloadVideo")
    public String downloadVideo(@RequestParam String videoId, @RequestParam String resolution) {
        downloadService.downloadVideo(videoId, resolution);
        return "Descargando video: " + videoId + " con resolución: " + resolution;
    }

    @GetMapping("/downloadMusic")
    public String downloadMusic(@RequestParam String videoId, @RequestParam String format) {
        downloadService.downloadMusic(videoId, format);
        return "Descargando música: " + videoId + " en formato: " + format;
    }
}
