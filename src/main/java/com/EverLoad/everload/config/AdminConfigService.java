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

    private static final String ACOUSTID_API_KEY = "acoustidApiKey";

    public static final String DEFAULT_AUTH_HERO_IMAGES = String.join("\n",
            "/api/music/artist-auto-image/david_guetta.jpg",
            "/api/music/artist-auto-image/aitana.jpg",
            "/api/music/artist-auto-image/maluma.jpg",
            "/api/music/artist-auto-image/daddy_yankee.jpg",
            "/api/music/artist-auto-image/inna.jpg",
            "/api/music/artist-auto-image/sash.jpg"
    );

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
                      .writeValue(f, defaultConfig());
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
        if (!f.exists()) return defaultConfig();
        Map<String, String> cfg = new HashMap<>(mapper.readValue(f, Map.class));
        cfg.putIfAbsent(ACOUSTID_API_KEY, "");
        cfg.putIfAbsent("githubToken", "");
        cfg.putIfAbsent("authHeroImages", DEFAULT_AUTH_HERO_IMAGES);
        return cfg;
    }

    private Map<String, String> defaultConfig() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("clientId", "");
        cfg.put("clientSecret", "");
        cfg.put("apiKey", "");
        cfg.put(ACOUSTID_API_KEY, "");
        cfg.put("githubToken", "");
        cfg.put("authHeroImages", DEFAULT_AUTH_HERO_IMAGES);
        return cfg;
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

    public String getAcoustidApiKey() throws IOException {
        return getConfig().getOrDefault(ACOUSTID_API_KEY, "");
    }
}
