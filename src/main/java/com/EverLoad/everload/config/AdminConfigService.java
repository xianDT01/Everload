package com.EverLoad.everload.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AdminConfigService {

    @Value("${app.config.path:./config.json}")
    private String configPath;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    void ensureConfigExists() {
        File f = new File(configPath);
        if (!f.exists()) {
            try {
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                mapper.writerWithDefaultPrettyPrinter()
                      .writeValue(f, Map.of("clientId", "", "clientSecret", "", "apiKey", ""));
                log.info("Created empty config.json at: {}", f.getAbsolutePath());
            } catch (IOException e) {
                log.warn("Could not create config.json at '{}': {}", f.getAbsolutePath(), e.getMessage());
            }
        } else {
            log.info("Using config.json at: {}", f.getAbsolutePath());
        }
    }

    public Map<String, String> getConfig() throws IOException {
        File f = new File(configPath);
        if (!f.exists()) return new HashMap<>(Map.of("clientId", "", "clientSecret", "", "apiKey", ""));
        return mapper.readValue(f, Map.class);
    }

    public void updateConfig(Map<String, String> newConfig) throws IOException {
        File f = new File(configPath);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(f, newConfig);
        log.info("Config updated at: {}", f.getAbsolutePath());
    }

    public String getClientId() throws IOException {
        return getConfig().getOrDefault("clientId", "");
    }

    public String getClientSecret() throws IOException {
        return getConfig().getOrDefault("clientSecret", "");
    }

    public String getApiKey() throws IOException {
        return getConfig().getOrDefault("apiKey", "");
    }
}
