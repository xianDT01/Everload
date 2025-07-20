package com.EverLoad.everload.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class DownloadService {

    private static final String DOWNLOADS_DIR = "./downloads/";

    public ResponseEntity<FileSystemResource> downloadVideo(String videoId, String resolution) {
        try {
            String tempDir = createTempDownloadDir();
            String command = String.format(
                    "yt-dlp --print after_move:filepath " +
                            "-f bestvideo[height=%s]+bestaudio/best " +
                            "-o %s%%(title)s.%%(ext)s " +
                            "https://www.youtube.com/watch?v=%s",
                    resolution, tempDir, videoId
            );
            return executeCommand(command);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    public ResponseEntity<FileSystemResource> downloadMusic(String videoId, String format) {
        try {
            String tempDir = createTempDownloadDir();
            String command = String.format(
                    "yt-dlp --ignore-errors --print after_move:filepath " +
                            "-x --audio-format %s " +
                            "-o %s%%(title)s.%%(ext)s " +
                            "https://www.youtube.com/watch?v=%s",
                    format, tempDir, videoId
            );

            return executeCommand(command);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public ResponseEntity<?> getPlaylistVideos(String playlistUrl) {
        try {
            String command = String.format("yt-dlp --flat-playlist --print %%(title)s|%%(id)s %s", playlistUrl);
            Process process = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            List<Map<String, String>> videos = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length == 2) {
                    Map<String, String> video = new HashMap<>();
                    video.put("title", parts[0]);
                    video.put("id", parts[1]);
                    videos.add(video);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error ejecutando yt-dlp");
            }

            return ResponseEntity.ok(videos);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno");
        }
    }




    private ResponseEntity<FileSystemResource> executeCommand(String command) {
        try {
            System.out.println("🔵 Ejecutando comando: " + command);

            Process process = Runtime.getRuntime().exec(command);

            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            new Thread(() -> {
                String line;
                try {
                    while ((line = errorReader.readLine()) != null) {
                        if (!line.contains("nsig extraction failed")) {
                            System.out.println("⚠️ YT-DLP ERROR: " + line);
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            int exitCode = process.waitFor();
            String finalPath = outputReader.readLine();

            outputReader.close();
            errorReader.close();

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

    private String createTempDownloadDir() {
        String tempDirName = DOWNLOADS_DIR + "tmp-" + UUID.randomUUID();
        File tempDir = new File(tempDirName);
        if (!tempDir.exists()) tempDir.mkdirs();
        return tempDirName + "/";
    }

    private ResponseEntity<FileSystemResource> sendFile(File file) {
        HttpHeaders headers = new HttpHeaders();
        String safeName = makeAsciiSafe(file.getName());

        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/octet-stream");

        System.out.println("📤 Enviando archivo: " + file.getAbsolutePath() + " con header: " + safeName);

        FileSystemResource resource = new FileSystemResource(file);

        // Elimina el directorio temporal completo después de 5 segundos
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                File parentDir = file.getParentFile();
                Files.walk(parentDir.toPath())
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(f -> {
                            if (f.delete()) {
                                System.out.println("🧹 Eliminado: " + f.getAbsolutePath());
                            } else {
                                System.out.println("⚠️ No se pudo eliminar: " + f.getAbsolutePath());
                            }
                        });
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }).start();

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }

    private String makeAsciiSafe(String input) {
        input = input.replace("\"", "'");
        return input.replaceAll("[^\\p{Print}]", "_")
                .replaceAll("[\\\\/:*?\"<>|｜]", "_");
    }


    public ResponseEntity<FileSystemResource> downloadTwitterVideo(String tweetUrl) {
        try {
            String tempDir = createTempDownloadDir();
            String command = String.format(
                    "yt-dlp --print after_move:filepath -o %s%%(title)s.%%(ext)s %s",
                    tempDir, tweetUrl
            );
            return executeCommand(command);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public ResponseEntity<FileSystemResource> downloadFacebookVideo(String videoUrl) {
        try {
            String tempDir = createTempDownloadDir();
            String command = String.format(
                    "yt-dlp --print after_move:filepath -o %s%%(title)s.%%(ext)s %s",
                    tempDir, videoUrl
            );
            return executeCommand(command);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public ResponseEntity<FileSystemResource> downloadInstagramVideo(String videoUrl) {
        try {
            String tempDir = createTempDownloadDir();
            String command = String.format(
                    "yt-dlp --print after_move:filepath -o %s%%(title)s.%%(ext)s %s",
                    tempDir, videoUrl
            );
            return executeCommand(command);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



}
