package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.MaintenanceStatusDto;
import org.springframework.stereotype.Service;

/**
 * In-memory maintenance mode state.
 * Volatile fields ensure visibility across threads without a DB round-trip.
 */
@Service
public class MaintenanceService {

    private static final String DEFAULT_MESSAGE =
            "La aplicación está en mantenimiento por los administradores. Inténtalo de nuevo en unos minutos.";

    private volatile boolean active = false;
    private volatile String message = DEFAULT_MESSAGE;

    public boolean isActive() { return active; }
    public String getMessage() { return message; }

    public MaintenanceStatusDto getStatus() {
        return new MaintenanceStatusDto(active, message);
    }

    public void activate(String customMessage) {
        if (customMessage != null && !customMessage.isBlank()) {
            this.message = customMessage;
        }
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void updateMessage(String customMessage) {
        if (customMessage != null && !customMessage.isBlank()) {
            this.message = customMessage;
        }
    }
}