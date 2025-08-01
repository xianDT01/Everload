package com.EverLoad.everload.controller;

import com.EverLoad.everload.service.HistorialDescargasService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class LimpiarTempController {

    private final HistorialDescargasService historial;

    public LimpiarTempController(HistorialDescargasService historial) {
        this.historial = historial;
    }

    @Operation(summary = "Eliminar carpetas temporales de descargas")
    @GetMapping("/limpiarTemp")
    public ResponseEntity<String> limpiarTemporales() {
        boolean ok = historial.limpiarTemporales();
        return ok
                ? ResponseEntity.ok("🧹 Carpetas temporales eliminadas.")
                : ResponseEntity.status(500).body("❌ Error al eliminar carpetas temporales");
    }
}
