package com.EverLoad.everload.service;

import com.EverLoad.everload.model.DownloadLog;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class LogService {
    private static final String LOG_FILE = "download-logs.csv";

    public void log(String platform, String videoId, boolean success, String error) {
        DownloadLog log = new DownloadLog(LocalDateTime.now(), platform, videoId, success, error);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            writer.write(String.format("%s,%s,%s,%s,%s%n",
                    log.getTimestamp(), log.getPlatform(), log.getVideoId(), log.isSuccess(), log.getError()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<DownloadLog> getLogs() {
        List<DownloadLog> list = new ArrayList<>();
        File file = new File(LOG_FILE);
        if (!file.exists()) {
            return list;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            for (String line : lines) {
                String[] parts = line.split(",", 5);
                if (parts.length == 5) {
                    DownloadLog log = new DownloadLog(
                            LocalDateTime.parse(parts[0]),
                            parts[1],
                            parts[2],
                            Boolean.parseBoolean(parts[3]),
                            parts[4].equals("null") ? null : parts[4]
                    );
                    list.add(log);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }
}
