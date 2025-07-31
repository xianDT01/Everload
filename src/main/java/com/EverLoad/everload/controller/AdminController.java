package com.EverLoad.everload.controller;

import com.EverLoad.everload.config.CredentialConfig;
import com.EverLoad.everload.model.DownloadLog;
import com.EverLoad.everload.service.LogService;
import com.EverLoad.everload.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:4200")
public class AdminController {

    private final CredentialConfig credentialConfig;
    private final LogService logService;

    @Value("${admin.username}")
    private String adminUser;
    @Value("${admin.password}")
    private String adminPass;

    private final JwtUtil jwtUtil;

    public AdminController(CredentialConfig credentialConfig, LogService logService, JwtUtil jwtUtil) {
        this.credentialConfig = credentialConfig;
        this.logService = logService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> body) {
        String user = body.get("username");
        String pass = body.get("password");
        if (adminUser.equals(user) && adminPass.equals(pass)) {
            String token = jwtUtil.generateToken(user);
            Map<String, String> resp = new HashMap<>();
            resp.put("token", token);
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.status(401).build();
    }

    @GetMapping("/credentials")
    public CredentialConfig getCredentials() {
        return credentialConfig;
    }

    @PostMapping("/credentials")
    public ResponseEntity<Void> updateCredentials(@RequestBody CredentialConfig cfg) {
        credentialConfig.setClientId(cfg.getClientId());
        credentialConfig.setClientSecret(cfg.getClientSecret());
        credentialConfig.setYoutubeApiKey(cfg.getYoutubeApiKey());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/logs")
    public List<DownloadLog> logs() {
        return logService.getLogs();
    }
}
