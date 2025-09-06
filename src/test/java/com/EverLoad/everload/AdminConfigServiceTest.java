package com.EverLoad.everload;

import com.EverLoad.everload.config.AdminConfigService;
import org.junit.jupiter.api.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdminConfigServiceTest {

    private AdminConfigService service;
    private File configFile;

    @BeforeEach
    void setUp() throws IOException {
        // crear un config.json temporal en la raíz del proyecto de test
        configFile = new File("config.json");
        if (configFile.exists()) {
            assertTrue(configFile.delete(), "No se pudo limpiar config.json existente");
        }
        Files.writeString(configFile.toPath(),
                "{ \"clientId\": \"CID\", \"clientSecret\": \"CSEC\", \"apiKey\": \"API\" }");

        service = new AdminConfigService();
    }

    @AfterEach
    void tearDown() {
        if (configFile.exists()) {
            assertTrue(configFile.delete(), "No se pudo borrar config.json de prueba");
        }
    }

    @Test
    void getConfig_leeArchivoCorrectamente() throws IOException {
        Map<String, String> cfg = service.getConfig();
        assertEquals("CID", cfg.get("clientId"));
        assertEquals("CSEC", cfg.get("clientSecret"));
        assertEquals("API", cfg.get("apiKey"));
    }


    @Test
    void updateConfig_sobrescribeArchivo() throws IOException {
        Map<String, String> nuevo = Map.of(
                "clientId", "NEW_ID",
                "clientSecret", "NEW_SECRET",
                "apiKey", "NEW_API"
        );
    }

    @Test
    void getValoresIndividuales_funcionan() throws IOException {
        assertEquals("CID", service.getClientId());
        assertEquals("CSEC", service.getClientSecret());
        assertEquals("API", service.getApiKey());
    }
}