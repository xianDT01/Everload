package com.everload.everload.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YtMusicInnertubeClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private YtMusicInnertubeClient client;
    private JsonNode okResponse;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate = mock(RestTemplate.class);
        client = new YtMusicInnertubeClient(restTemplate);
        okResponse = mapper.readTree("{\"ok\":true}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(okResponse));
    }

    @Test
    void browseBuildsAnonymousWebContextAndSupportsOptionalParams() {
        assertEquals(okResponse, client.browse("FEmusic_home"));
        assertEquals(okResponse, client.browse("FEmusic_moods_and_genres", "params-1"));

        ExchangeCalls calls = captureCalls(2);
        assertTrue(calls.urls().get(0).endsWith("/youtubei/v1/browse?prettyPrint=false"));
        assertEquals("FEmusic_home", body(calls.entities().get(0)).get("browseId"));
        assertFalse(body(calls.entities().get(0)).containsKey("params"));
        assertEquals("params-1", body(calls.entities().get(1)).get("params"));

        Map<String, Object> context = nestedMap(body(calls.entities().get(0)), "context");
        Map<String, Object> contextClient = nestedMap(context, "client");
        assertEquals("WEB_REMIX", contextClient.get("clientName"));
        assertEquals("en", contextClient.get("hl"));
        assertAnonymousWebHeaders(calls.entities().get(0).getHeaders());
    }

    @Test
    void searchSuggestionsAndNextSendExpectedBodies() {
        client.search("query", YtMusicInnertubeClient.SONGS_FILTER);
        client.searchSuggestions("que");
        Map<String, Object> nextBody = Map.of("videoId", "abc", "playlistId", "RDAMVMabc");
        client.next(nextBody);

        ExchangeCalls calls = captureCalls(3);
        assertTrue(calls.urls().get(0).contains("/search?"));
        assertEquals("query", body(calls.entities().get(0)).get("query"));
        assertEquals(YtMusicInnertubeClient.SONGS_FILTER, body(calls.entities().get(0)).get("params"));
        assertTrue(calls.urls().get(1).contains("get_search_suggestions"));
        assertEquals("que", body(calls.entities().get(1)).get("input"));
        assertTrue(calls.urls().get(2).contains("/next?"));
        assertEquals(nextBody, body(calls.entities().get(2)));
    }

    @Test
    void playerAddsProofVisitorAndEmbeddedContextForWebClient() {
        YtMusicClient embedded = new YtMusicClient(
                "WEB_EMBEDDED_PLAYER", "1.0", "99", "agent",
                "", "", "", "", null, false, true);

        client.player(embedded, "video-1", "proof-token", "visitor-token");

        ExchangeCalls calls = captureCalls(1);
        assertTrue(calls.urls().get(0).startsWith("https://www.youtube.com/youtubei/v1/player"));
        Map<String, Object> request = body(calls.entities().get(0));
        assertEquals("video-1", request.get("videoId"));
        assertEquals(Map.of("poToken", "proof-token"), request.get("serviceIntegrityDimensions"));
        Map<String, Object> context = nestedMap(request, "context");
        assertEquals("visitor-token", nestedMap(context, "client").get("visitorData"));
        assertEquals(Map.of("embedUrl", "https://www.youtube.com/watch?v=video-1"), context.get("thirdParty"));
        assertEquals("https://music.youtube.com", calls.entities().get(0).getHeaders().getFirst("Origin"));
    }

    @Test
    void androidPlayerIncludesDeviceContextWithoutWebOriginHeaders() {
        client.player(YtMusicClient.ANDROID_VR_1_43_32, "video-2", null, null);

        ExchangeCalls calls = captureCalls(1);
        HttpEntity<?> entity = calls.entities().get(0);
        Map<String, Object> request = body(entity);
        Map<String, Object> contextClient = nestedMap(nestedMap(request, "context"), "client");
        assertEquals("Android", contextClient.get("osName"));
        assertEquals("Oculus", contextClient.get("deviceMake"));
        assertEquals(32, contextClient.get("androidSdkVersion"));
        assertFalse(request.containsKey("serviceIntegrityDimensions"));
        assertFalse(entity.getHeaders().containsKey("Origin"));
        assertFalse(entity.getHeaders().containsKey(HttpHeaders.COOKIE));
        assertFalse(entity.getHeaders().containsKey(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void fetchVisitorDataReturnsTokenAndRejectsMissingValue() throws Exception {
        JsonNode visitor = mapper.readTree("{\"responseContext\":{\"visitorData\":\"visitor-123\"}}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(visitor));

        assertEquals("visitor-123", client.fetchVisitorData());

        JsonNode missing = mapper.readTree("{\"responseContext\":{}}");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(missing));
        assertThrows(YtMusicTransportException.class, client::fetchVisitorData);
    }

    @Test
    void transportErrorsAndEmptyBodiesAreWrappedWithOperationContext() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new IllegalStateException("offline"));

        YtMusicTransportException browseError = assertThrows(
                YtMusicTransportException.class, () -> client.browse("home"));
        assertTrue(browseError.getMessage().contains("browse(home)"));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok().build());
        YtMusicTransportException emptyError = assertThrows(
                YtMusicTransportException.class, () -> client.searchSuggestions("x"));
        assertTrue(emptyError.getMessage().contains("searchSuggestions"));
    }

    private ExchangeCalls captureCalls(int count) {
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(count)).exchange(
                urlCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(JsonNode.class));
        return new ExchangeCalls(urlCaptor.getAllValues(), entityCaptor.getAllValues());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(HttpEntity<?> entity) {
        return (Map<String, Object>) entity.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }

    private void assertAnonymousWebHeaders(HttpHeaders headers) {
        assertEquals("https://music.youtube.com", headers.getFirst("Origin"));
        assertEquals("67", headers.getFirst("X-YouTube-Client-Name"));
        assertFalse(headers.containsKey(HttpHeaders.COOKIE));
        assertFalse(headers.containsKey(HttpHeaders.AUTHORIZATION));
    }

    private record ExchangeCalls(List<String> urls, List<HttpEntity<?>> entities) {}
}
