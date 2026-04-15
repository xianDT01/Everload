package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.SystemInfoDto;
import com.EverLoad.everload.dto.UpdateCheckDto;
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

@Slf4j
@Service
public class SystemInfoService {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${spring.datasource.url:jdbc:h2:file:./everload-db}")
    private String datasourceUrl;

    @Value("${app.update.check-url:}")
    private String updateCheckUrl;

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
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(updateCheckUrl))
                    .header("User-Agent", "EverLoad/" + appVersion)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

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

    // ── Parsers ────────────────────────────────────────────────────────────────

    /**
     * Parses the GitHub Commits API response.
     * URL: /repos/{owner}/{repo}/commits/{branch}
     * Compares the deployed commit hash against the latest commit on the branch.
     */
    private UpdateCheckDto parseCommitsApiResponse(String body) {
        String deployedCommit = readDeployedCommit();

        String latestSha    = extractJsonString(body, "sha");
        String htmlUrl      = extractJsonString(body, "html_url");
        String commitMsg    = extractJsonString(body, "message");
        String commitDate   = extractJsonString(body, "date");

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

        boolean updateAvailable = !latestSha.isBlank()
                && !deployedCommit.equals("unknown")
                && !latestSha.startsWith(deployedCommit)
                && !deployedCommit.startsWith(latestShort);

        return UpdateCheckDto.builder()
                .checkConfigured(true)
                .updateAvailable(updateAvailable)
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
    private UpdateCheckDto parseReleasesApiResponse(String body) {
        String latestVersion = extractJsonString(body, "tag_name").replaceFirst("^v", "");
        String releaseUrl    = extractJsonString(body, "html_url");
        String releaseNotes  = extractJsonString(body, "body");
        if (releaseNotes.length() > 500) releaseNotes = releaseNotes.substring(0, 500) + "…";

        boolean updateAvailable = !latestVersion.isBlank() && !latestVersion.equals(appVersion);

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
        try {
            ClassPathResource res = new ClassPathResource("git-commit.txt");
            if (!res.exists()) return "unknown";
            return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "unknown";
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String formatBytes(long bytes) {
        if (bytes < 1_024) return bytes + " B";
        if (bytes < 1_048_576) return String.format("%.1f KB", bytes / 1_024.0);
        return String.format("%.1f MB", bytes / 1_048_576.0);
    }

    /** Minimal JSON string extractor — avoids an extra Jackson call for this single use. */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return "";
        int start = json.indexOf('"', idx + search.length());
        if (start == -1) return "";
        int end = start + 1;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        return json.substring(start + 1, end)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}