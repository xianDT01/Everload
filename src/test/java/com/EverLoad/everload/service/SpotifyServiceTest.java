package com.everload.everload.service;

import com.everload.everload.config.AdminConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class SpotifyServiceTest {

    private RestTemplate restTemplate;
    private AdminConfigService configService;
    private SpotifyService service;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        configService = mock(AdminConfigService.class);
        service = new SpotifyService(restTemplate, configService);
        when(configService.getApiKey()).thenReturn("key");
    }

    @Test
    void youtubeSearchReturnsNullForEmptyProviderResponse() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(null);

        String result = ReflectionTestUtils.invokeMethod(service, "searchYouTube", "Artist - Song");

        assertNull(result);
    }

    @Test
    void youtubeSearchReturnsFirstVideoResult() {
        Map<String, Object> video = Map.of(
                "id", Map.of("kind", "youtube#video", "videoId", "abc123")
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("items", List.of(video)));

        String result = ReflectionTestUtils.invokeMethod(service, "searchYouTube", "Artist - Song");

        assertEquals("https://www.youtube.com/watch?v=abc123", result);
    }

    @Test
    void playlistEmbedParsesTracksAndRespectsLimit() throws Exception {
        mockEmbed(200, """
                <script id="__NEXT_DATA__" type="application/json">{
                  "props":{"pageProps":{"state":{"data":{"entity":{"trackList":[
                    {"title":" First Song ","subtitle":"Artist"},
                    {"title":"Second","subtitle":""},
                    {"title":"","subtitle":"ignored"}
                  ]}}}}}
                }</script>
                """);
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(Map.of("items", List.of(Map.of(
                        "id", Map.of("kind", "youtube#video", "videoId", "youtube-id")))));

        var tracks = service.getPlaylistTracks("playlist-id", 1);

        assertEquals(1, tracks.size());
        assertEquals("Artist - First Song", tracks.get(0).getTitle());
        assertEquals("Artist", tracks.get(0).getArtist());
        assertEquals("https://www.youtube.com/watch?v=youtube-id", tracks.get(0).getYoutubeUrl());
    }

    @Test
    void playlistEmbedRejectsHttpErrorsAndMissingOrMalformedData() throws Exception {
        mockEmbed(503, "offline");
        assertThrows(SpotifyServiceException.class, () -> service.getPlaylistTracks("playlist"));

        mockEmbed(200, "no embedded json");
        assertThrows(SpotifyServiceException.class, () -> service.getPlaylistTracks("playlist"));

        mockEmbed(200, "<script id=\"__NEXT_DATA__\" type=\"application/json\">{</script>");
        assertThrows(SpotifyServiceException.class, () -> service.getPlaylistTracks("playlist"));
    }

    @Test
    void playlistEmbedReturnsEmptyWhenTrackListIsMissingAndExtractsPlaylistIds() throws Exception {
        mockEmbed(200, """
                <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{}}}</script>
                """);

        assertEquals(List.of(), service.getPlaylistTracks("playlist"));
        assertEquals("abc123", service.extractPlaylistId("https://open.spotify.com/playlist/abc123?si=token"));
        assertThrows(IllegalArgumentException.class, () -> service.extractPlaylistId("https://example.test/"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockEmbed(int status, String body) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
    }
}
