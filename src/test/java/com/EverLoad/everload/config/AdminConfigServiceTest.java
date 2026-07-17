package com.EverLoad.everload.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void missingConfigurationReturnsCompleteDefaults() throws IOException {
        AdminConfigService service = service(tempDir.resolve("missing.json"));

        Map<String, String> config = service.getConfig();

        assertEquals("", config.get("clientId"));
        assertEquals("", config.get("clientSecret"));
        assertEquals("", config.get("apiKey"));
        assertEquals("", config.get("acoustidApiKey"));
        assertEquals("", config.get("githubToken"));
        assertEquals(AdminConfigService.DEFAULT_AUTH_HERO_IMAGES, config.get("authHeroImages"));
    }

    @Test
    void initializationCreatesParentAndDefaultFile() throws IOException {
        Path configPath = tempDir.resolve("nested/config.json");
        AdminConfigService service = service(configPath);

        service.ensureConfigExists();

        assertTrue(Files.isRegularFile(configPath));
        assertEquals(AdminConfigService.DEFAULT_AUTH_HERO_IMAGES, service.getConfig().get("authHeroImages"));
    }

    @Test
    void updatePersistsValuesAndConvenienceGettersReadThem() throws IOException {
        Path configPath = tempDir.resolve("config.json");
        AdminConfigService service = service(configPath);
        Map<String, String> values = Map.of(
                "clientId", "client",
                "clientSecret", "secret",
                "apiKey", "youtube",
                "acoustidApiKey", "acoustid"
        );

        service.updateConfig(values);

        assertEquals("client", service.getClientId());
        assertEquals("secret", service.getClientSecret());
        assertEquals("youtube", service.getApiKey());
        assertEquals("acoustid", service.getAcoustidApiKey());
        assertEquals("", service.getConfig().get("githubToken"));
        assertEquals(AdminConfigService.DEFAULT_AUTH_HERO_IMAGES, service.getConfig().get("authHeroImages"));
    }

    @Test
    void initializationKeepsAnExistingConfiguration() throws IOException {
        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, "{\"clientId\":\"existing\"}");
        AdminConfigService service = service(configPath);

        service.ensureConfigExists();

        assertEquals("existing", service.getClientId());
    }

    private AdminConfigService service(Path configPath) {
        AdminConfigService service = new AdminConfigService();
        ReflectionTestUtils.setField(service, "configPath", configPath.toString());
        return service;
    }
}
