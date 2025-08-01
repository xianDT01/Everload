package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.Descarga;
import com.EverLoad.everload.service.HistorialDescargasService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
@RestController
@RequestMapping("/api/admin/historial")
public class HistorialDescargasController {


        private final HistorialDescargasService historial;

        public HistorialDescargasController(HistorialDescargasService historial) {
            this.historial = historial;
        }

        @GetMapping
        public ResponseEntity<List<Descarga>> verHistorial() {
            return ResponseEntity.ok(historial.getHistorial());
        }


    }

