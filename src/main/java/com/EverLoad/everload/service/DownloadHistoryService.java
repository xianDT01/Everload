package com.EverLoad.everload.service;

import com.EverLoad.everload.model.Download;
import com.EverLoad.everload.repository.DownloadRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class DownloadHistoryService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DownloadHistoryService.class);
    private static final String LEGACY_HISTORY_PATH = "downloads_history.json";

    private final DownloadRepository downloadRepository;
    private final ObjectMapper mapper;

    public DownloadHistoryService(DownloadRepository downloadRepository, ObjectMapper mapper) {
        this.downloadRepository = downloadRepository;
        this.mapper = mapper;
    }

    /** Importa una sola vez el historial del antiguo downloads_history.json a la BD. */
    @PostConstruct
    void importLegacyJsonHistory() {
        File legacy = new File(LEGACY_HISTORY_PATH);
        if (!legacy.exists() || downloadRepository.count() > 0) return;
        try {
            List<Download> history = mapper.readValue(legacy, new TypeReference<List<Download>>() {});
            if (!history.isEmpty()) {
                downloadRepository.saveAll(history);
                logger.info("Historial de descargas migrado a BD: {} entradas desde {}", history.size(), LEGACY_HISTORY_PATH);
            }
            if (!legacy.renameTo(new File(LEGACY_HISTORY_PATH + ".imported"))) {
                logger.warn("No se pudo renombrar {} tras la importación", LEGACY_HISTORY_PATH);
            }
        } catch (Exception e) {
            logger.warn("No se pudo importar el historial legado {}: {}", LEGACY_HISTORY_PATH, e.getMessage());
        }
    }

    public void recordDownload(Download download) {
        try {
            downloadRepository.save(download);
        } catch (Exception e) {
            logger.error("No se pudo guardar el historial de descargas", e);
        }
    }

    public List<Download> getHistory() {
        return downloadRepository.findAll(Sort.by("id"));
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
                            logger.warn("No se pudo borrar el directorio temporal {}", path, e);
                        }
                    });
            return true;
        } catch (IOException e) {
            logger.error("Error limpiando directorios temporales", e);
            return false;
        }
    }

    public void clearHistory() {
        downloadRepository.deleteAllInBatch();
    }
}
