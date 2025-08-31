package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.AdminConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
