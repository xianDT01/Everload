package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
import com.EverLoad.everload.model.Descarga;
import com.EverLoad.everload.service.HistorialDescargasService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class AdminController {

    private final AdminConfigService configService;


    public AdminController(AdminConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> getConfig() {
        try {
            return ResponseEntity.ok(configService.getConfig());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<Void> updateConfig(@RequestBody Map<String, String> newConfig) {
        try {
            configService.updateConfig(newConfig);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }




}
