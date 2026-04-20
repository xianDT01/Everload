package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.MaintenanceStatusDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Maintenance mode state — persisted to a flag file so it survives Docker restarts.
 *
 * Flag file location is configured via app.maintenance.flag-path.
 * In Docker, set APP_MAINTENANCE_FLAG=/app/data/maintenance.flag (on the DB persistent volume).
 */
@Slf4j
@Service
public class MaintenanceService {

    private static final String DEFAULT_MESSAGE =
            "La aplicación está en mantenimiento por los administradores. Inténtalo de nuevo en unos minutos.";

    @Value("${app.maintenance.flag-path:./maintenance.flag}")
    private String flagFilePath;

    private volatile boolean active = false;
    private volatile String message = DEFAULT_MESSAGE;

    @PostConstruct
    void loadFromDisk() {
        Path flag = Path.of(flagFilePath);
        if (!Files.exists(flag)) return;
        try {
            List<String> lines = Files.readAllLines(flag);
            this.active = true;
            this.message = (!lines.isEmpty() && !lines.get(0).isBlank())
                    ? lines.get(0) : DEFAULT_MESSAGE;
            log.info("Maintenance mode restored from flag file: {}", flag);
        } catch (IOException e) {
            log.warn("Could not read maintenance flag file '{}': {}", flag, e.getMessage());
        }
    }

    public boolean isActive() { return active; }
    public String getMessage() { return message; }

    public MaintenanceStatusDto getStatus() {
        return new MaintenanceStatusDto(active, message);
    }

    public void activate(String customMessage) {
        this.message = (customMessage != null && !customMessage.isBlank()) ? customMessage : DEFAULT_MESSAGE;
        this.active = true;
        writeFlagFile();
    }

    public void deactivate() {
        this.active = false;
        deleteFlagFile();
    }

    public void updateMessage(String customMessage) {
        if (customMessage != null && !customMessage.isBlank()) {
            this.message = customMessage;
            if (this.active) writeFlagFile();
        }
    }

    private void writeFlagFile() {
        Path flag = Path.of(flagFilePath);
        try {
            if (flag.getParent() != null) Files.createDirectories(flag.getParent());
            Files.writeString(flag, this.message);
        } catch (IOException e) {
            log.error("Could not write maintenance flag file '{}': {}", flag, e.getMessage());
        }
    }

    private void deleteFlagFile() {
        try {
            Files.deleteIfExists(Path.of(flagFilePath));
        } catch (IOException e) {
            log.warn("Could not delete maintenance flag file '{}': {}", flagFilePath, e.getMessage());
        }
    }
}
