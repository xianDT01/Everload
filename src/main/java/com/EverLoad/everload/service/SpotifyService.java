package com.EverLoad.everload.service;

import com.EverLoad.everload.config.CredentialConfig;
import com.EverLoad.everload.model.SpotifyResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpotifyService {

    private final RestTemplate restTemplate;
    @Autowired
    private CredentialConfig credentialConfig;

    public SpotifyService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public SpotifyService(RestTemplate restTemplate, CredentialConfig credentialConfig) {
        this.restTemplate = restTemplate;
        this.credentialConfig = credentialConfig;
    }

    public String getAccessToken() {
        String url = "https://accounts.spotify.com/api/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String id = credentialConfig != null ? credentialConfig.getClientId() : "";
        String secret = credentialConfig != null ? credentialConfig.getClientSecret() : "";
        String auth = id + ":" + secret;
        headers.setBasicAuth(Base64.getEncoder().encodeToString(auth.getBytes()));

        HttpEntity<String> request = new HttpEntity<>("grant_type=client_credentials", headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            return (String) response.getBody().get("access_token");
        }

        throw new RuntimeException("No se pudo obtener el token de Spotify");
    }

    public List<SpotifyResult> getPlaylistTracks(String playlistId) {
        String token = getAccessToken();
        String url = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Error al obtener la playlist de Spotify");
        }

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");

        return items.stream().map(item -> {
            Map<String, Object> track = (Map<String, Object>) item.get("track");
            String name = (String) track.get("name");

            List<Map<String, String>> artists = (List<Map<String, String>>) track.get("artists");
            String artistNames = artists.stream()
                    .map(a -> a.get("name"))
                    .collect(Collectors.joining(", "));

            String query = artistNames + " - " + name;
            String youtubeUrl = searchYouTube(query);
            return new SpotifyResult(query, youtubeUrl);

        }).collect(Collectors.toList());
    }

    private String searchYouTube(String rawTitle) {
        String apiKey = credentialConfig != null ? credentialConfig.getYoutubeApiKey() : "";

        // Limpieza y normalización del título
        String cleaned = rawTitle
                .replaceAll("[\\(\\)\\[\\]\"']", "")   // elimina paréntesis, corchetes y comillas
                .replaceAll("[^\\w\\s-]", "")          // elimina símbolos raros
                .replaceAll("\\s+", " ")               // múltiples espacios -> uno solo
                .trim();

        String query = cleaned + " lyrics";

        try {
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
                return idWithParams.split("[?&]")[0]; // mejor que solo "\\?"
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        throw new IllegalArgumentException("URL de playlist inválida");
    }

}
