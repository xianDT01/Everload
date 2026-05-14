package com.EverLoad.everload.service;

import com.EverLoad.everload.config.AdminConfigService;
import com.EverLoad.everload.model.SpotifyResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpotifyService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern NEXT_DATA_PATTERN =
            Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);

    private final RestTemplate restTemplate;
    private final AdminConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SpotifyService(RestTemplate restTemplate, AdminConfigService configService) {
        this.restTemplate = restTemplate;
        this.configService = configService;
    }

    public List<SpotifyResult> getPlaylistTracks(String playlistId) {
        try {
            return getPlaylistTracksFromEmbed(playlistId);
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron obtener las canciones de Spotify: " + e.getMessage(), e);
        }
    }

    private List<SpotifyResult> getPlaylistTracksFromEmbed(String playlistId) throws Exception {
        String url = "https://open.spotify.com/embed/playlist/" + playlistId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Spotify embed devolvió HTTP " + response.statusCode());
        }

        String html = response.body();
        Matcher matcher = NEXT_DATA_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new RuntimeException("No se encontraron datos en el embed de Spotify. Comprueba que la playlist sea pública.");
        }

        JsonNode data = objectMapper.readTree(matcher.group(1));
        JsonNode trackList = data
                .path("props").path("pageProps").path("state")
                .path("data").path("entity").path("trackList");

        if (!trackList.isArray() || trackList.size() == 0) {
            return Collections.emptyList();
        }

        List<SpotifyResult> results = new ArrayList<>();
        for (JsonNode track : trackList) {
            String title = track.path("title").asText("").trim();
            String artist = track.path("subtitle").asText("").trim();
            if (title.isEmpty()) continue;

            String query = artist.isEmpty() ? title : artist + " - " + title;
            String youtubeUrl = searchYouTube(query);
            results.add(new SpotifyResult(query, youtubeUrl));
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private String searchYouTube(String rawTitle) {
        try {
            String apiKey = configService.getApiKey();

            String cleaned = rawTitle
                    .replaceAll("[\\(\\)\\[\\]\"']", "")
                    .replaceAll("[^\\w\\s-]", "")
                    .replaceAll("\\s+", " ")
                    .trim();

            String query = cleaned + " lyrics";
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

            String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=3&q="
                    + encodedQuery + "&key=" + apiKey;

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");

            if (items != null && !items.isEmpty()) {
                for (Map<String, Object> item : items) {
                    Map<String, Object> id = (Map<String, Object>) item.get("id");
                    if (id != null && "youtube#video".equals(id.get("kind"))) {
                        String videoId = (String) id.get("videoId");
                        if (videoId != null) {
                            return "https://www.youtube.com/watch?v=" + videoId;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Error buscando en YouTube: " + e.getMessage());
        }
        return null;
    }

    public String extractPlaylistId(String url) {
        try {
            if (url.contains("playlist/")) {
                String[] parts = url.split("playlist/");
                String idWithParams = parts[1];
                return idWithParams.split("[?&]")[0];
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new IllegalArgumentException("URL de playlist inválida");
    }
}
