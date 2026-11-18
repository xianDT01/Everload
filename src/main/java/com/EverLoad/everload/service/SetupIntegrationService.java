package com.everload.everload.service;

import com.everload.everload.config.AdminConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class SetupIntegrationService {
    private final AdminConfigService config;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Autowired
    public SetupIntegrationService(AdminConfigService config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    SetupIntegrationService(AdminConfigService config, HttpClient client) {
        this.config = config;
        this.client = client;
    }

    public Map<String, Boolean> configured() throws IOException {
        return Map.of("spotify", !config.getClientId().isBlank() && !config.getClientSecret().isBlank(),
                "youtube", !config.getApiKey().isBlank(), "acoustid", !config.getAcoustidApiKey().isBlank());
    }

    public void save(String service, Map<String, String> supplied) throws IOException {
        Set<String> keys = switch (service) {
            case "spotify" -> Set.of("clientId", "clientSecret");
            case "youtube" -> Set.of("apiKey");
            case "acoustid" -> Set.of("acoustidApiKey");
            default -> throw new IllegalArgumentException("SETUP_SERVICE");
        };
        if (!keys.equals(supplied.keySet()) || supplied.values().stream()
                .anyMatch(value -> value == null || value.isBlank() || value.length() > 512
                        || value.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("SETUP_FIELDS");
        }
        config.mergeIntegrationConfig(supplied);
    }

    public String verify(String service) throws IOException {
        if ("acoustid".equals(service)) return "unverified";
        HttpRequest.Builder request;
        if ("spotify".equals(service)) {
            if (!configured().get("spotify")) return "missing";
            String credentials = config.getClientId() + ":" + config.getClientSecret();
            request = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                    .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"));
        } else if ("youtube".equals(service)) {
            if (!configured().get("youtube")) return "missing";
            request = HttpRequest.newBuilder(URI.create("https://www.googleapis.com/youtube/v3/videos?part=id&id=jNQXAC9IVRw"))
                    .header("X-Goog-Api-Key", config.getApiKey()).GET();
        } else throw new IllegalArgumentException("SETUP_SERVICE");
        try {
            var response = client.send(request.timeout(Duration.ofSeconds(8)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return "failed";
            var body = mapper.readTree(response.body());
            boolean valid = "spotify".equals(service) ? !body.path("access_token").asText().isBlank()
                    : "youtube#videoListResponse".equals(body.path("kind").asText());
            return valid ? "verified" : "failed";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "failed";
        } catch (IOException | IllegalArgumentException e) {
            // Never return provider bodies, exception URLs or credentials to the browser/logs.
            return "failed";
        }
    }
}
