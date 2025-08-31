package com.EverLoad.everload.service;

import com.EverLoad.everload.model.Download;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
@Service
public class DownloadHistoryService {
    private static final String DOWNLOAD_HISTORY_PATH = "downloads_history.json";
    private final ObjectMapper mapper;

    public DownloadHistoryService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public synchronized void recordDownload(Download download) {
        try {
            List<Download> history = getHistory();
            history.add(download);
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(DOWNLOAD_HISTORY_PATH), history);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized List<Download> getHistory() {
        try {
            File file = new File(DOWNLOAD_HISTORY_PATH);
            if (!file.exists()) return new ArrayList<>();
            return new ArrayList<>(mapper.readValue(file, new TypeReference<List<Download>>() {}));
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    public boolean clearTemporaryFolders() {
        try {
            Path tempDir = Paths.get("./downloads");
            if (!Files.exists(tempDir)) return true;

            Files.walk(tempDir)
                    .filter(path -> Files.isDirectory(path) && path.getFileName().toString().startsWith("tmp-"))
                    .forEach(path -> {
                        try {
                            FileUtils.deleteDirectory(path.toFile());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized void clearHistory() {
        try {
            File file = new File(DOWNLOAD_HISTORY_PATH);
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, new ArrayList<>());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}