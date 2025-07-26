package com.EverLoad.everload.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TendenciasController {

    @GetMapping("/tendencias")
    public ResponseEntity<String> getTendencias() {
        try {
            ProcessBuilder pb = new ProcessBuilder("venv-tendencias/bin/python3", "generar_tendencias_ytdlp.py");
            pb.directory(new java.io.File(".")); // directorio actual del proyecto
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\": \"Error ejecutando el script\"}");
            }

            return ResponseEntity.ok(output.toString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"error\": \"Excepción: " + e.getMessage() + "\"}");
        }
    }
}
