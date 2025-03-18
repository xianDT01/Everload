package com.EverLoad.everload.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

@Service
public class DownloadService {

    private static final String DOWNLOADS_DIR = "./downloads/";

    public ResponseEntity<FileSystemResource> downloadVideo(String videoId, String resolution) {
        String basePath = DOWNLOADS_DIR + videoId + "_" + resolution;
        String command = "yt-dlp -f bestvideo[height=" + resolution + "]+bestaudio/best -o " + basePath + " https://www.youtube.com/watch?v=" + videoId;
        return executeCommand(command, basePath);
    }

    public ResponseEntity<FileSystemResource> downloadMusic(String videoId, String format) {
        String basePath = DOWNLOADS_DIR + videoId;
        String command = "yt-dlp -x --audio-format " + format + " -o " + basePath + ".%(ext)s https://www.youtube.com/watch?v=" + videoId;
        return executeCommand(command, basePath);
    }

    private ResponseEntity<FileSystemResource> executeCommand(String command, String basePath) {
        try {
            System.out.println("🔵 Ejecutando comando: " + command);
            Process process = Runtime.getRuntime().exec(command);


            Scanner scanner = new Scanner(process.getErrorStream());
            while (scanner.hasNextLine()) {
                System.out.println("⚠️ YT-DLP ERROR: " + scanner.nextLine());
            }
            scanner.close();

            process.waitFor();


            System.out.println("📂 Archivos en " + DOWNLOADS_DIR + ":");
            File downloadDir = new File(DOWNLOADS_DIR);
            for (File file : downloadDir.listFiles()) {
                System.out.println("📄 " + file.getName());
            }


            File[] matchingFiles = downloadDir.listFiles((dir, name) ->
                    name.startsWith(new File(basePath).getName()));

            if (matchingFiles != null && matchingFiles.length > 0) {
                File file = matchingFiles[0];
                System.out.println("✅ Archivo encontrado: " + file.getAbsolutePath());
                return sendFile(file);
            } else {
                System.out.println("❌ ERROR: No se encontró el archivo descargado.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("❌ Error al ejecutar yt-dlp.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    private ResponseEntity<FileSystemResource> sendFile(File file) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");

        System.out.println("📤 Enviando archivo: " + file.getAbsolutePath());

        FileSystemResource resource = new FileSystemResource(file);


        new Thread(() -> {
            try {
                Thread.sleep(5000); // Espera 5 segundos antes de eliminar el archivo
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
}