package com.EverLoad.everload.service;

import java.io.IOException;

import org.springframework.stereotype.Service;

@Service
public class DownloadService {

    public void downloadVideo(String videoId, String resolution) {
        String command = "./yt-dlp -f bestvideo[height=" + resolution + "]+bestaudio/best " + videoId;
        try {
            // Ejecuta el comando yt-dlp
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor(); // Espera que el proceso termine
            System.out.println("Video descargado: " + videoId);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error al descargar el video.");
        }
    }

    public void downloadMusic(String videoId, String format) {
        String command = "./yt-dlp -x --audio-format " + format + " " + videoId;
        try {
            // Ejecuta el comando yt-dlp
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor(); // Espera que el proceso termine
            System.out.println("Música descargada: " + videoId);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error al descargar la música.");
        }
    }
}
