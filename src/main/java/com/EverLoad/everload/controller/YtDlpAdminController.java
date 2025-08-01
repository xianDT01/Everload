package com.EverLoad.everload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class YtDlpAdminController {

    @PostMapping("/update-yt-dlp")
    public ResponseEntity<String> updateYtDlp() {
        try {
            Process process = Runtime.getRuntime().exec("yt-dlp -U");
            int exitCode = process.waitFor();
            return ResponseEntity.ok("✅ yt-dlp actualizado. Código de salida: " + exitCode);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("❌ Error actualizando yt-dlp: " + e.getMessage());
        }
    }


}