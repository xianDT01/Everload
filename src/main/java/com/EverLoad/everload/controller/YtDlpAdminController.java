package com.EverLoad.everload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;

@RestController
@RequestMapping("/api/admin")
public class YtDlpAdminController {

    @PostMapping("/update-yt-dlp")
    public ResponseEntity<String> updateYtDlp() {
        try {
            File workingDir = new File("/home/xiandt/IdeaProjects/Everload");
            File executable = new File(workingDir, "yt-dlp");

            if (!executable.exists()) {
                return ResponseEntity.status(404).body("❌ yt-dlp no encontrado en: " + executable.getAbsolutePath());
            }
            if (!executable.canExecute()) {
                return ResponseEntity.status(403).body("❌ yt-dlp no tiene permisos de ejecución");
            }

            Process process = new ProcessBuilder("./yt-dlp", "-U")
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            return ResponseEntity.ok("✅ yt-dlp actualizado.\n\nExit code: " + exitCode + "\n\nSalida:\n" + output);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ Excepción al actualizar yt-dlp: " + e.getMessage());
        }
    }



}