package com.EverLoad.everload.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;

@Service
public class DownloadService {

    private static final String DOWNLOADS_DIR = "./downloads/";

    public ResponseEntity<FileSystemResource> downloadVideo(String videoId, String resolution) {
        String command = "yt-dlp -f bestvideo[height=" + resolution + "]+bestaudio/best " +
                "-o " + DOWNLOADS_DIR + "%(title)s.%(ext)s " +
                "https://www.youtube.com/watch?v=" + videoId;
        return executeCommand(command);
    }


    public ResponseEntity<FileSystemResource> downloadMusic(String videoId, String format) {
        String command = "yt-dlp -x --audio-format " + format + " " +
                "-o " + DOWNLOADS_DIR + "%(title)s.%(ext)s " +
                "https://www.youtube.com/watch?v=" + videoId;
        return executeCommand(command);
    }


    private ResponseEntity<FileSystemResource> executeCommand(String command) {
        try {
            System.out.println("🔵 Ejecutando comando: " + command);
            Process process = Runtime.getRuntime().exec(command);

            Scanner scanner = new Scanner(process.getErrorStream());
            while (scanner.hasNextLine()) {
                System.out.println("⚠️ YT-DLP ERROR: " + scanner.nextLine());
            }
            scanner.close();

            process.waitFor();

            File downloadDir = new File(DOWNLOADS_DIR);
            File[] matchingFiles = downloadDir.listFiles(File::isFile);

            if (matchingFiles == null || matchingFiles.length == 0) {
                System.out.println("❌ No se encontraron archivos en la carpeta.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            // Ordenar archivos por fecha de modificación (más reciente primero)
            Arrays.sort(matchingFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            File file = matchingFiles[0];

            System.out.println("✅ Archivo encontrado: " + file.getAbsolutePath());
            return sendFile(file);

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