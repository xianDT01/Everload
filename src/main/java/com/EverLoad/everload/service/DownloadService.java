package com.EverLoad.everload.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * DownloadService con limpieza de caracteres no ASCII al enviar el archivo
 */
@Service
public class DownloadService {

    private static final String DOWNLOADS_DIR = "./downloads/";

    public ResponseEntity<FileSystemResource> downloadVideo(String videoId, String resolution) {
        try {
            // Usa --print after_move:filepath para capturar la ruta final real tras el merge
            String command = String.format(
                    "yt-dlp --print after_move:filepath "
                            + "-f bestvideo[height=%s]+bestaudio/best "
                            + "-o \"%s%%(title)s.%%(ext)s\" "
                            + "https://www.youtube.com/watch?v=%s",
                    resolution, DOWNLOADS_DIR, videoId
            );
            return executeCommand(command);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public ResponseEntity<FileSystemResource> downloadMusic(String videoId, String format) {
        try {
            // Mismo esquema para audio
            String command = String.format(
                    "yt-dlp --print after_move:filepath "
                            + "-x --audio-format %s "
                            + "-o \"%s%%(title)s.%%(ext)s\" "
                            + "https://www.youtube.com/watch?v=%s",
                    format, DOWNLOADS_DIR, videoId
            );
            return executeCommand(command);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Ejecuta el comando yt-dlp, lee la ruta final desde after_move:filepath
     * y devuelve el archivo al cliente.
     */
    private ResponseEntity<FileSystemResource> executeCommand(String command) {
        try {
            System.out.println("🔵 Ejecutando comando: " + command);

            Process process = Runtime.getRuntime().exec(command);

            // Output normal (stdout) -> donde after_move:filepath imprime el path final
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            // Errores (stderr)
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            // Lee errores en un hilo aparte para logging
            new Thread(() -> {
                String line;
                try {
                    while ((line = errorReader.readLine()) != null) {
                        System.out.println("⚠️ YT-DLP ERROR: " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            int exitCode = process.waitFor();

            // La primera línea de stdout es la ruta final
            String finalPath = outputReader.readLine();

            // Cerramos buffers
            outputReader.close();
            errorReader.close();

            // Verificamos que yt-dlp terminó bien
            if (exitCode != 0 || finalPath == null || finalPath.isEmpty()) {
                System.out.println("❌ yt-dlp terminó con error o no devolvió la ruta final.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            System.out.println("✅ Ruta final tras descarga: " + finalPath);

            File finalFile = new File(finalPath);
            if (!finalFile.exists()) {
                System.out.println("❌ El archivo indicado por yt-dlp no existe: " + finalPath);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            return sendFile(finalFile);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("❌ Error al ejecutar yt-dlp.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Envía el archivo al cliente, usando un nombre "ASCII-safe" para Content-Disposition.
     */
    private ResponseEntity<FileSystemResource> sendFile(File file) {
        HttpHeaders headers = new HttpHeaders();

        // Nombre seguro: quitamos caracteres no ASCII o problemáticos
        String safeName = makeAsciiSafe(file.getName());

        // Armamos la cabecera Content-Disposition
        // Tomcat rechazará el header si contiene caracteres fuera de 0..255
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");

        System.out.println("📤 Enviando archivo: " + file.getAbsolutePath() + " con header: " + safeName);

        FileSystemResource resource = new FileSystemResource(file);

        // Elimina el archivo tras 5s, para no saturar carpeta de descargas
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if (file.delete()) {
                    System.out.println("✅ Archivo eliminado: " + file.getAbsolutePath());
                } else {
                    System.out.println("⚠️ No se pudo eliminar el archivo: " + file.getAbsolutePath());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    /**
     * Reemplaza caracteres no ASCII por '_' para evitar rechazo de Tomcat/HTTP
     * en el header "Content-Disposition".
     */
    private String makeAsciiSafe(String input) {
        // Tomcat 10 quita el header si encuentra caracteres fuera del rango ISO-8859-1.
        // Este replaceAll elimina/caracteres raros y también comillas que rompen la sintaxis.
        input = input.replace("\"", "'"); // Evita comillas en medio
        // Reemplaza cada carácter que no esté en [32..126] (ASCII imprimible) por '_'
        // (Salvo que quieras permitir algunos más, p.ej. tildes. Pero aquí es lo más seguro)
        return input.replaceAll("[^\\x20-\\x7E]", "_");
    }
}
