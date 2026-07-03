package com.EverLoad.everload.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Búsqueda en YouTube vía yt-dlp (ytsearch), sin gastar cuota de la Data API.
 * Devuelve una estructura compatible con la respuesta de YouTube Data API v3
 * para que el frontend no necesite cambios.
 */
@Service
public class YouTubeSearchService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(YouTubeSearchService.class);

    /** Si yt-dlp no responde en este tiempo se mata el proceso (antes colgaba el hilo HTTP). */
    private static final long SEARCH_TIMEOUT_SECONDS = 30;

    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ytsearch-watchdog");
        t.setDaemon(true);
        return t;
    });

    public List<Map<String, Object>> search(String query, int maxResults) throws IOException, InterruptedException {
        // ytSearch se pasa como argumento ÚNICO a ProcessBuilder — sin shell, sin inyección
        String ytSearch = "ytsearch" + maxResults + ":" + query;
        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--flat-playlist",
                "--print", "%(id)s\t%(title)s\t%(uploader)s\t%(duration)s\t%(thumbnails.0.url)s",
                "--no-warnings",
                ytSearch
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // destroyForcibly cierra stdout → el readLine del bucle desbloquea con EOF
        ScheduledFuture<?> kill = watchdog.schedule(() -> {
            if (process.isAlive()) {
                logger.warn("yt-dlp search superó {}s — matando proceso (query: {})", SEARCH_TIMEOUT_SECONDS, query);
                process.destroyForcibly();
            }
        }, SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        List<Map<String, Object>> items = new ArrayList<>();
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t", 5);
                    if (parts.length < 2) continue;
                    String id       = parts[0].trim();
                    String title    = parts[1].trim();
                    String uploader = parts.length > 2 ? parts[2].trim() : "";

                    // Estructura compatible con YouTube Data API v3
                    items.add(Map.of(
                        "id", Map.of("videoId", id),
                        "snippet", Map.of(
                            "title", title,
                            "channelTitle", uploader,
                            "thumbnails", Map.of(
                                "default", Map.of("url", "https://img.youtube.com/vi/" + id + "/default.jpg"),
                                "medium",  Map.of("url", "https://img.youtube.com/vi/" + id + "/mqdefault.jpg"),
                                "high",    Map.of("url", "https://img.youtube.com/vi/" + id + "/hqdefault.jpg")
                            )
                        )
                    ));
                }
            }
            process.waitFor();
        } finally {
            kill.cancel(false);
            if (process.isAlive()) process.destroyForcibly();
        }
        return items;
    }
}
