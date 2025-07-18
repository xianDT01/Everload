package com.EverLoad.everload.service;

import com.EverLoad.everload.model.SpotifyResult;
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
    private final String clientId = "9656c5e6ac6540629503b41dca0b4392";
    private final String clientSecret = "7a3004e38e83431daf3d6380255f551e";

    public SpotifyService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getAccessToken() {
        String url = "https://accounts.spotify.com/api/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String auth = clientId + ":" + clientSecret;
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
        String apiKey = "AIzaSyCVzVmbSB5YVeYzOfiUtw3Hx_J58nGytxI";

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
            return url.split("playlist/")[1].split("\\?")[0];
        } catch (Exception e) {
            throw new IllegalArgumentException("URL de playlist inválida");
        }
    }
}