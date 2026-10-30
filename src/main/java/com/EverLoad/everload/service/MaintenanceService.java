package com.everload.everload.service;

import com.everload.everload.dto.MaintenanceStatusDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
            String saved = Files.readString(flag);
            this.message = saved.isBlank() ? DEFAULT_MESSAGE : saved;
            this.active = true;
            log.info("Maintenance mode restored from flag file: {}", flag);
        } catch (IOException e) {
            this.active = true;
            log.warn("Could not read maintenance flag file '{}': {}", flag, e.getMessage());
        }
    }

    public boolean isActive() { return active; }
    public String getMessage() { return message; }

    public synchronized MaintenanceStatusDto getStatus() {
        return new MaintenanceStatusDto(active, message);
    }

    public synchronized void activate(String customMessage) {
        String nextMessage = (customMessage != null && !customMessage.isBlank()) ? customMessage : DEFAULT_MESSAGE;
        writeFlagFile(nextMessage);
        this.message = nextMessage;
        this.active = true;
    }

    public synchronized void deactivate() {
        deleteFlagFile();
        this.active = false;
    }

    public synchronized void updateMessage(String customMessage) {
        if (customMessage != null && !customMessage.isBlank()) {
            if (this.active) writeFlagFile(customMessage);
            this.message = customMessage;
        }
    }

    private void writeFlagFile(String nextMessage) {
        Path flag = Path.of(flagFilePath).toAbsolutePath();
        Path temporary = null;
        try {
            Files.createDirectories(flag.getParent());
            temporary = Files.createTempFile(flag.getParent(), "maintenance-", ".tmp");
            Files.writeString(temporary, nextMessage);
            try {
                Files.move(temporary, flag, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, flag, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Could not write maintenance flag file '{}': {}", flag, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar el estado de mantenimiento", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { log.warn("Could not remove temporary maintenance file", e); }
            }
        }
    }

    private void deleteFlagFile() {
        try {
            Files.deleteIfExists(Path.of(flagFilePath));
        } catch (IOException e) {
            log.warn("Could not delete maintenance flag file '{}': {}", flagFilePath, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo desactivar el mantenimiento de forma persistente", e);
        }
    }
}
