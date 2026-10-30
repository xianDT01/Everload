package com.everload.everload.service;

import com.everload.everload.config.AdminConfigService;
import com.everload.everload.dto.SystemInfoDto;
import com.everload.everload.dto.UpdateCheckDto;
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
                  "sha" : "1234567890abcdef",
                  "html_url" : "https://github.test/commit/123",
                  "commit": {"message":"First line\\nSecond line",
                    "committer":{"date":"2026-07-13T10:00:00Z"}},
                  "author":{"html_url":"https://github.test/not-the-commit"}
                }
                """;

        UpdateCheckDto result = ReflectionTestUtils.invokeMethod(service, "parseCommitsApiResponse", body);

        assertEquals("1234567", result.getLatestCommit());
        assertEquals("First line", result.getCommitMessage());
        assertEquals("https://github.test/commit/123", result.getCommitUrl());
        assertFalse(result.isUpdateAvailable());
        assertNotNull(result.getError());
    }

    @Test
    void releasesDoNotOfferDowngradesAndParseEscapedJson() {
        UpdateCheckDto older = ReflectionTestUtils.invokeMethod(service, "parseReleasesApiResponse",
                "{\"tag_name\":\"v1.9\",\"body\":\"Text \\\"quoted\\\"\\nnext line\"}");
        assertFalse(older.isUpdateAvailable());
        assertEquals("Text \"quoted\"\nnext line", older.getReleaseNotes());
        UpdateCheckDto newer = ReflectionTestUtils.invokeMethod(service, "parseReleasesApiResponse",
                "{\"tag_name\":\"v2.10.0\"}");
        assertTrue(newer.isUpdateAvailable());
    }

    @Test
    void customUpdateEndpointsNeverReceiveTheGithubToken() throws Exception {
        var authorization = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/releases/latest", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"tag_name\":\"v2.1.0\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            when(configService.getConfig()).thenReturn(Map.of("githubToken", "test-token-not-a-real-credential"));
            ReflectionTestUtils.setField(service, "updateCheckUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/releases/latest");
            assertTrue(service.checkUpdate().isUpdateAvailable());
            org.junit.jupiter.api.Assertions.assertNull(authorization.get());
        } finally { server.stop(0); }
    }

    @Test
    void dirtyBuildCannotBePresentedAsUpToDate() throws Exception {
        try (MockedConstruction<ClassPathResource> ignored = mockConstruction(ClassPathResource.class,
                (resource, context) -> {
                    when(resource.exists()).thenReturn(true);
                    when(resource.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(
                            "1234567-dirty".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                })) {
            UpdateCheckDto result = ReflectionTestUtils.invokeMethod(service, "parseCommitsApiResponse",
                    "{\"sha\":\"1234567890abcdef\",\"commit\":{\"message\":\"Example\"}}");
            assertFalse(result.isUpdateAvailable());
            assertNotNull(result.getError());
        }
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
