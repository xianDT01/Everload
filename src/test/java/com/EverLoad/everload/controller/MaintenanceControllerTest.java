package com.everload.everload.controller;

import com.everload.everload.dto.MaintenanceStatusDto;
import com.everload.everload.service.MaintenanceService;
import com.everload.everload.service.AuditLogService;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenanceControllerTest {
    @Test
    void publicStatusIsNotCacheableAndMalformedUpdatesCannotDisableMaintenance() {
        MaintenanceService service = mock(MaintenanceService.class);
        MaintenanceController controller = new MaintenanceController(service, mock(AuditLogService.class));
        when(service.getStatus()).thenReturn(new MaintenanceStatusDto(true, "Working"));
        assertEquals("no-store", controller.publicStatus().getHeaders().getFirst("Cache-Control"));
        assertEquals(400, controller.setStatus(Map.of()).getStatusCode().value());
        verify(service, never()).deactivate();
    }

    @Test
    void savingAnInactiveMessageUpdatesTheMessageWithoutActivatingMaintenance() {
        MaintenanceService service = mock(MaintenanceService.class);
        MaintenanceController controller = new MaintenanceController(service, mock(AuditLogService.class));
        controller.setStatus(Map.of("active", false, "message", "Next maintenance"));
        verify(service).updateMessage("Next maintenance");
        verify(service, never()).activate(any());
    }
}
