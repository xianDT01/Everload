package com.everload.everload.service;

import com.everload.everload.config.AdminConfigService;
import com.everload.everload.dto.SystemInfoDto;
import com.everload.everload.dto.UpdateCheckDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.math.BigInteger;

@Slf4j
@Service
public class SystemInfoService {

    private static final String UNKNOWN_COMMIT = "unknown";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${spring.datasource.url:jdbc:h2:file:./everload-db}")
    private String datasourceUrl;

    @Value("${app.update.check-url:}")
    private String updateCheckUrl;

    private final AdminConfigService adminConfigService;

    public SystemInfoService(AdminConfigService adminConfigService) {
        this.adminConfigService = adminConfigService;
    }

    // ── System info ────────────────────────────────────────────────────────────

    public SystemInfoDto getInfo() {
        String javaVersion = System.getProperty("java.version");
        long uptimeSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        String dbFilePath = datasourceUrl
                .replace("jdbc:h2:file:", "")
                .split(";")[0]
                .trim();
        String dbAbsPath = new File(dbFilePath + ".mv.db").getAbsolutePath();
        File dbFile = new File(dbAbsPath);
        long dbSizeBytes = dbFile.exists() ? dbFile.length() : 0;

        return SystemInfoDto.builder()
                .appVersion(appVersion)
                .currentCommit(readDeployedCommit())
                .javaVersion(javaVersion)
                .uptimeSeconds(uptimeSec)
                .dbPath(dbAbsPath)
                .dbSizeBytes(dbSizeBytes)
                .dbSizeFormatted(formatBytes(dbSizeBytes))
                .build();
    }

    // ── Update check ───────────────────────────────────────────────────────────

    public UpdateCheckDto checkUpdate() {
        if (updateCheckUrl == null || updateCheckUrl.isBlank()) {
            return UpdateCheckDto.builder()
                    .currentVersion(appVersion)
                    .checkConfigured(false)
                    .build();
        }

        try {
            String githubToken = loadGithubToken();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();

            URI checkUri = URI.create(updateCheckUrl.replace("{branch}",
                    URLEncoder.encode(readBuildResource("git-branch.txt", "main"), StandardCharsets.UTF_8)));
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(checkUri)
                    .header("User-Agent", "EverLoad/" + appVersion)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .GET();
            if (!githubToken.isBlank() && "https".equalsIgnoreCase(checkUri.getScheme())
                    && "api.github.com".equalsIgnoreCase(checkUri.getHost())
                    && (checkUri.getPort() == -1 || checkUri.getPort() == 443)
                    && checkUri.getUserInfo() == null) {
                reqBuilder.header("Authorization", "Bearer " + githubToken);
            }
            HttpRequest request = reqBuilder.build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return UpdateCheckDto.builder()
                        .currentVersion(appVersion)
                        .currentCommit(readDeployedCommit())
                        .checkConfigured(true)
                        .error("HTTP " + response.statusCode())
                        .build();
            }

            String body = response.body();

            // Detect API type from URL and parse accordingly
            if (updateCheckUrl.contains("/commits/")) {
                return parseCommitsApiResponse(body);
            } else {
                return parseReleasesApiResponse(body);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[UPDATE] Check interrupted");
            return UpdateCheckDto.builder()
                    .currentVersion(appVersion)
                    .currentCommit(readDeployedCommit())
                    .checkConfigured(true)
                    .error("Comprobacion interrumpida")
                    .build();
        } catch (Exception e) {
            log.warn("[UPDATE] Check failed: {}", e.getMessage());
            return UpdateCheckDto.builder()
                    .currentVersion(appVersion)
                    .currentCommit(readDeployedCommit())
                    .checkConfigured(true)
                    .error(e.getMessage())
                    .build();
        }
    }

    private String loadGithubToken() {
        try {
            return adminConfigService.getConfig().getOrDefault("githubToken", "");
        } catch (Exception e) {
            log.debug("GitHub token unavailable; checking updates anonymously: {}", e.getMessage());
            return "";
        }
    }

    // ── Parsers ────────────────────────────────────────────────────────────────

    /**
     * Parses the GitHub Commits API response.
     * URL: /repos/{owner}/{repo}/commits/{branch}
     * Compares the deployed commit hash against the latest commit on the branch.
     */
    private UpdateCheckDto parseCommitsApiResponse(String body) throws IOException {
        String deployedCommit = readDeployedCommit();
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.isObject()) throw new IOException("Respuesta de revision vacia o no valida");
        String latestSha = root.path("sha").asText("");
        if (!latestSha.matches("[a-fA-F0-9]{7,40}")) throw new IOException("Respuesta de revision no valida");
        String htmlUrl = root.path("html_url").asText("");
        String commitMsg = root.path("commit").path("message").asText("");
        String commitDate = root.path("commit").path("committer").path("date").asText("");

        // GitHub returns the full SHA — we only show the first 7 chars
        String latestShort   = latestSha.length() >= 7 ? latestSha.substring(0, 7) : latestSha;
        String deployedShort = deployedCommit.length() >= 7
                ? deployedCommit.substring(0, 7)
                : deployedCommit;

        // First line of commit message only
        String shortMsg = commitMsg.contains("\n")
                ? commitMsg.substring(0, commitMsg.indexOf('\n')).trim()
                : commitMsg.trim();
        if (shortMsg.length() > 80) shortMsg = shortMsg.substring(0, 80) + "…";

        boolean comparable = deployedCommit.matches("[a-fA-F0-9]{7,40}");
        boolean updateAvailable = comparable
                && !latestSha.toLowerCase(java.util.Locale.ROOT).startsWith(deployedCommit.toLowerCase(java.util.Locale.ROOT))
                && !deployedCommit.toLowerCase(java.util.Locale.ROOT).startsWith(latestSha.toLowerCase(java.util.Locale.ROOT));

        return UpdateCheckDto.builder()
                .checkConfigured(true)
                .updateAvailable(updateAvailable)
                .error(comparable ? null : "La compilacion no tiene una revision limpia identificable; no se puede confirmar si esta actualizada")
                .currentCommit(deployedShort)
                .latestCommit(latestShort)
                .commitMessage(shortMsg)
                .commitDate(commitDate)
                .commitUrl(htmlUrl)
                // Keep version fields populated so the frontend always has something to show
                .currentVersion(appVersion)
                .latestVersion(latestShort)
                .build();
    }

    /**
     * Parses the GitHub Releases API response (kept for compatibility).
     * URL: /repos/{owner}/{repo}/releases/latest
     */
    private UpdateCheckDto parseReleasesApiResponse(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.isObject()) throw new IOException("Respuesta de version vacia o no valida");
        String latestVersion = root.path("tag_name").asText("").replaceFirst("^v", "");
        String releaseUrl = root.path("html_url").asText("");
        String releaseNotes = root.path("body").asText("");
        if (releaseNotes.length() > 500) releaseNotes = releaseNotes.substring(0, 500) + "…";

        String currentVersion = appVersion.replaceFirst("^v", "");
        if (!latestVersion.matches("\\d+(?:\\.\\d+){0,3}")
                || !currentVersion.matches("\\d+(?:\\.\\d+){0,3}")
                || root.path("draft").asBoolean() || root.path("prerelease").asBoolean()) {
            throw new IOException("No se puede comparar esta version con una publicacion estable");
        }
        String[] latestParts = latestVersion.split("\\.");
        String[] currentParts = currentVersion.split("\\.");
        int comparison = 0;
        for (int i = 0; i < Math.max(latestParts.length, currentParts.length) && comparison == 0; i++) {
            BigInteger latest = new BigInteger(i < latestParts.length ? latestParts[i] : "0");
            BigInteger current = new BigInteger(i < currentParts.length ? currentParts[i] : "0");
            comparison = latest.compareTo(current);
        }
        boolean updateAvailable = comparison > 0;

        return UpdateCheckDto.builder()
                .checkConfigured(true)
                .updateAvailable(updateAvailable)
                .currentVersion(appVersion)
                .latestVersion(latestVersion)
                .releaseUrl(releaseUrl)
                .releaseNotes(releaseNotes)
                .build();
    }

    // ── Deployed commit ────────────────────────────────────────────────────────

    /**
     * Reads the git commit hash written to the .jar's classpath during Docker build.
     * Returns "unknown" if the file is not present (e.g. local dev without Docker).
     */
    private String readDeployedCommit() {
        return readBuildResource("git-commit.txt", UNKNOWN_COMMIT);
    }

    private String readBuildResource(String name, String fallback) {
        try {
            ClassPathResource res = new ClassPathResource(name);
            if (!res.exists()) return fallback;
            try (InputStream stream = res.getInputStream()) {
                String value = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
                return value.isBlank() ? fallback : value;
            }
        } catch (Exception e) {
            return fallback;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String formatBytes(long bytes) {
        if (bytes < 1_024) return bytes + " B";
        if (bytes < 1_048_576) return String.format("%.1f KB", bytes / 1_024.0);
        return String.format("%.1f MB", bytes / 1_048_576.0);
    }

}
