package com.EverLoad.everload.service;

import com.EverLoad.everload.model.Descarga;
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
public class HistorialDescargasService {
    private final String HISTORIAL_PATH = "descargas.json";
    private final ObjectMapper mapper;

    public HistorialDescargasService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public synchronized void registrarDescarga(Descarga descarga) {
        try {
            List<Descarga> historial = getHistorial();
            historial.add(descarga);
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(HISTORIAL_PATH), historial);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized List<Descarga> getHistorial() {
        try {
            File file = new File(HISTORIAL_PATH);
            if (!file.exists()) return new ArrayList<>();
            return new ArrayList<>(mapper.readValue(file, new TypeReference<List<Descarga>>() {}));
        } catch (Exception e) {
            e.printStackTrace(); // Muy importante ver errores
            return Collections.emptyList();
        }
    }

    public boolean limpiarTemporales() {
        try {
            Path tempDir = Paths.get("./downloads");
            Files.walk(tempDir)
                    .filter(path -> Files.isDirectory(path) && path.getFileName().toString().startsWith("tmp-"))
                    .forEach(path -> {
                        try {
                            FileUtils.deleteDirectory(path.toFile()); // commons-io
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

}
