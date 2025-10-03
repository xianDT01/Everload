package com.EverLoad.everload.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

@Tag(name = "Admin", description = "Operaciones administrativas (yt-dlp, configuración, etc.)")
@RestController
@RequestMapping("/api/admin")
public class YtDlpAdminController {

    @Operation(
            summary = "Actualizar yt-dlp",
            description = "Ejecuta `yt-dlp -U` en el directorio del proyecto para actualizar la herramienta. " +
                    "Devuelve 200 con la salida si se actualizó correctamente, 404 si no se encuentra, " +
                    "403 si no tiene permisos, o 500 si hay un error inesperado.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Actualización exitosa"),
                    @ApiResponse(responseCode = "404", description = "yt-dlp no encontrado en el directorio"),
                    @ApiResponse(responseCode = "403", description = "yt-dlp no tiene permisos de ejecución"),
                    @ApiResponse(responseCode = "500", description = "Error al intentar actualizar")
            }
    )
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
