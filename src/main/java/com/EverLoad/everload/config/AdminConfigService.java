package com.EverLoad.everload.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
public class AdminConfigService {
    private final String CONFIG_PATH = "config.json"; // raíz del proyecto
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, String> getConfig() throws IOException {
        return mapper.readValue(new File(CONFIG_PATH), Map.class);
    }

    public void updateConfig(Map<String, String> newConfig) throws IOException {
        System.out.println("📝 Intentando guardar nueva configuración: " + newConfig);
        File f = new File(CONFIG_PATH);
        System.out.println("📄 Ruta completa: " + f.getAbsolutePath());
        mapper.writerWithDefaultPrettyPrinter().writeValue(f, newConfig);
    }


    public String getClientId() throws IOException {
        return getConfig().get("clientId");
    }

    public String getClientSecret() throws IOException {
        return getConfig().get("clientSecret");
    }

    public String getApiKey() throws IOException {
        return getConfig().get("apiKey");
    }

}