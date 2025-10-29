package com.EverLoad.everload.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;

@Tag(name = "Admin", description = "Operaciones administrativas (yt-dlp, configuración, etc.)")
@RestController
@RequestMapping("/api/admin")
public class YtDlpAdminController {

    // ⬇⬇⬇ CLAVE: damos un valor por defecto vacío "" para que Spring arranque aunque no haya nada configurado
    @Value("${everload.ytdlp.path:}")
    private String ytDlpPath;

    @Operation(
            summary = "Actualizar yt-dlp",
            description = "Ejecuta `yt-dlp -U` usando la ruta configurada.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Actualización exitosa"),
                    @ApiResponse(responseCode = "400", description = "Ruta yt-dlp no configurada"),
                    @ApiResponse(responseCode = "404", description = "yt-dlp no encontrado"),
                    @ApiResponse(responseCode = "403", description = "yt-dlp sin permisos de ejecución"),
                    @ApiResponse(responseCode = "500", description = "Error al intentar actualizar")
            }
    )
    @PostMapping("/update-yt-dlp")
    public ResponseEntity<String> updateYtDlp() {

        // 0. ¿tenemos ruta configurada?
        if (ytDlpPath == null || ytDlpPath.isBlank()) {
            return ResponseEntity.status(400)
                    .body("❌ Ruta de yt-dlp no configurada (everload.ytdlp.path está vacía)");
        }

        File executable = new File(ytDlpPath);

        // 1. existe?
        if (!executable.exists()) {
            return ResponseEntity.status(404)
                    .body("❌ yt-dlp no encontrado en: " + executable.getAbsolutePath());
        }

        // 2. permisos?
        if (!executable.canExecute()) {
            return ResponseEntity.status(403)
                    .body("❌ yt-dlp no tiene permisos de ejecución en: " + executable.getAbsolutePath());
        }

        try {
            // 3. Ejecutar ./yt-dlp -U
            Process process = new ProcessBuilder(executable.getAbsolutePath(), "-U")
                    .directory(executable.getParentFile())
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            return ResponseEntity.ok(
                    "✅ yt-dlp actualizado.\n\nExit code: " + exitCode + "\n\nSalida:\n" + output
            );

        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(500)
                    .body("❌ Excepción al actualizar yt-dlp: " + e.getMessage());
        }
    }
}
