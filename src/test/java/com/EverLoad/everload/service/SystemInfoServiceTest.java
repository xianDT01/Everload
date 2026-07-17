package com.EverLoad.everload.service;

import com.EverLoad.everload.config.AdminConfigService;
import com.EverLoad.everload.dto.SystemInfoDto;
import com.EverLoad.everload.dto.UpdateCheckDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class SystemInfoServiceTest {

    @TempDir
    Path tempDir;

    private AdminConfigService configService;
    private SystemInfoService service;

    @BeforeEach
    void setUp() {
        configService = mock(AdminConfigService.class);
        service = new SystemInfoService(configService);
        ReflectionTestUtils.setField(service, "appVersion", "2.0");
        ReflectionTestUtils.setField(service, "datasourceUrl", "jdbc:h2:file:" + tempDir.resolve("db"));
        ReflectionTestUtils.setField(service, "updateCheckUrl", "");
    }

    @Test
    void getInfoReportsRuntimeAndDatabaseDetails() {
        SystemInfoDto info = service.getInfo();

        assertEquals("2.0", info.getAppVersion());
        assertNotNull(info.getJavaVersion());
        assertEquals(0, info.getDbSizeBytes());
    }

    @Test
    void checkUpdateReportsUnconfiguredStateWithoutNetwork() {
        UpdateCheckDto result = service.checkUpdate();

        assertFalse(result.isCheckConfigured());
        assertEquals("2.0", result.getCurrentVersion());
    }

    @Test
    void githubTokenLoaderUsesConfiguredValueAndFallsBackToEmpty() throws IOException {
        when(configService.getConfig()).thenReturn(Map.of("githubToken", "token"));
        assertEquals("token", ReflectionTestUtils.invokeMethod(service, "loadGithubToken"));

        when(configService.getConfig()).thenThrow(new IOException("unavailable"));
        assertEquals("", ReflectionTestUtils.invokeMethod(service, "loadGithubToken"));
    }

    @Test
    void configuredCheckLoadsTokenAndReportsInvalidEndpoint() throws IOException {
        ReflectionTestUtils.setField(service, "updateCheckUrl", ":invalid-uri");
        when(configService.getConfig()).thenReturn(Map.of("githubToken", "token"));

        UpdateCheckDto result = service.checkUpdate();

        assertTrue(result.isCheckConfigured());
        assertNotNull(result.getError());
    }

    @Test
    void commitParserMapsLatestCommitDetails() {
        String body = """
                {
                  "sha":"1234567890abcdef",
                  "html_url":"https://github.test/commit/123",
                  "message":"First line\\nSecond line",
                  "date":"2026-07-13T10:00:00Z"
                }
                """;

        UpdateCheckDto result = ReflectionTestUtils.invokeMethod(service, "parseCommitsApiResponse", body);

        assertEquals("1234567", result.getLatestCommit());
        assertEquals("First line", result.getCommitMessage());
        assertEquals("https://github.test/commit/123", result.getCommitUrl());
        assertFalse(result.isUpdateAvailable());
    }

    @Test
    void interruptedUpdateCheckPreservesFlagAndReturnsStableError() {
        ReflectionTestUtils.setField(service, "updateCheckUrl", "http://127.0.0.1:1/releases/latest");
        Thread.currentThread().interrupt();

        UpdateCheckDto result = service.checkUpdate();

        assertEquals("Comprobacion interrumpida", result.getError());
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void deployedCommitFallsBackWhenClasspathResourceCannotBeRead() {
        try (MockedConstruction<ClassPathResource> resources = mockConstruction(
                ClassPathResource.class,
                (resource, context) -> {
                    when(resource.exists()).thenReturn(true);
                    when(resource.getInputStream()).thenThrow(new IOException("unreadable"));
                })) {
            assertEquals("unknown", ReflectionTestUtils.invokeMethod(service, "readDeployedCommit"));
        }
    }
}
