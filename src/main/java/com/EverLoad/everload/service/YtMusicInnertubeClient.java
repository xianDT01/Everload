package com.EverLoad.everload.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.EverLoad.everload.service.YtMusicClient.ORIGIN_YOUTUBE_MUSIC;
import static com.EverLoad.everload.service.YtMusicClient.WEB_REMIX;

/**
 * Raw transport for YouTube Music's internal "InnerTube" endpoints
 * ({@code /browse}, {@code /search}, {@code /player}, {@code /next},
 * {@code /visitor_id}).
 *
 * <p><b>Anonymous only, by construction</b>: this client never reads,
 * stores, or sends a {@code Cookie} or {@code Authorization} header. It
 * only ever talks to the public catalogue surfaces — search, discovery,
 * public playlists, and stream resolution for non-restricted videos. There
 * is no code path here that could be extended into a sign-in flow without
 * rewriting this class.
 *
 * <p>The client identity tuples in {@link YtMusicClient} are the same
 * factual identifiers YouTube's own apps send — documented by public
 * reverse-engineering references (NewPipe, yt-dlp) — and carry no
 * credential of any kind.
 */
@Component
public class YtMusicInnertubeClient {

    private static final Logger log = LoggerFactory.getLogger(YtMusicInnertubeClient.class);
    private static final String WWW_YOUTUBE = "https://www.youtube.com";

    private final RestTemplate restTemplate;

    public YtMusicInnertubeClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ── Context builders ──────────────────────────────────────────────

    private Map<String, Object> buildClientContext(YtMusicClient client) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("clientName", client.clientName());
        ctx.put("clientVersion", client.clientVersion());
        ctx.put("hl", "en");
        ctx.put("gl", "US");
        if (!client.osName().isEmpty()) ctx.put("osName", client.osName());
        if (!client.osVersion().isEmpty()) ctx.put("osVersion", client.osVersion());
        if (!client.deviceMake().isEmpty()) ctx.put("deviceMake", client.deviceMake());
        if (!client.deviceModel().isEmpty()) ctx.put("deviceModel", client.deviceModel());
        if (client.androidSdkVersion() != null) ctx.put("androidSdkVersion", client.androidSdkVersion());
        return ctx;
    }

    private Map<String, Object> baseBody(YtMusicClient client) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("client", buildClientContext(client));
        context.put("user", Map.of("lockedSafetyMode", false));
        body.put("context", context);
        return body;
    }

    private HttpHeaders headersFor(YtMusicClient client, boolean webOrigin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", client.userAgent());
        headers.set("X-Goog-Api-Format-Version", "1");
        headers.set("X-YouTube-Client-Name", client.clientId());
        headers.set("X-YouTube-Client-Version", client.clientVersion());
        if (webOrigin) {
            headers.set("X-Origin", ORIGIN_YOUTUBE_MUSIC);
            headers.set("Origin", ORIGIN_YOUTUBE_MUSIC);
            headers.set("Referer", ORIGIN_YOUTUBE_MUSIC + "/");
        }
        return headers;
    }

    private JsonNode post(String url, Object body, HttpHeaders headers) {
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        JsonNode result = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class).getBody();
        if (result == null) {
            throw new YtMusicTransportException("Respuesta vacía de " + url);
        }
        return result;
    }

    // ── Browse ────────────────────────────────────────────────────────

    /** Anonymous browse by browse id — works for public surfaces (artists, albums, playlists, home feed). */
    public JsonNode browse(String browseId) {
        return browse(browseId, null);
    }

    /** Browse with optional {@code params} — required by mood/genre category pages. */
    public JsonNode browse(String browseId, String params) {
        Map<String, Object> body = baseBody(WEB_REMIX);
        body.put("browseId", browseId);
        if (params != null && !params.isBlank()) {
            body.put("params", params);
        }
        try {
            return post(ORIGIN_YOUTUBE_MUSIC + "/youtubei/v1/browse?prettyPrint=false",
                    body, headersFor(WEB_REMIX, true));
        } catch (Exception e) {
            throw new YtMusicTransportException("browse(" + browseId + ") falló: " + e.getMessage(), e);
        }
    }

    /** Browse continuation — paginates shelves, playlists, artist sections, etc. */
    public JsonNode browseContinuation(String continuation) {
        Map<String, Object> body = baseBody(WEB_REMIX);
        String url = ORIGIN_YOUTUBE_MUSIC + "/youtubei/v1/browse?ctoken=" + continuation
                + "&continuation=" + continuation + "&prettyPrint=false";
        try {
            return post(url, body, headersFor(WEB_REMIX, true));
        } catch (Exception e) {
            throw new YtMusicTransportException("browseContinuation falló: " + e.getMessage(), e);
        }
    }

    // ── Search ────────────────────────────────────────────────────────

    public static final String SONGS_FILTER = "EgWKAQIIAWoMEAMQBBAJEAoQDhAV";
    public static final String VIDEOS_FILTER = "EgWKAQIQAWoMEAMQBBAJEAoQDhAV";
    public static final String ARTISTS_FILTER = "EgWKAQIgAWoMEAMQBBAJEAoQDhAV";

    /** Raw {@code /search} call. {@code params} selects a results tab filter (songs/videos/artists); null = "Top result". */
    public JsonNode search(String query, String params) {
        Map<String, Object> body = baseBody(WEB_REMIX);
        body.put("query", query);
        if (params != null) {
            body.put("params", params);
        }
        try {
            return post(ORIGIN_YOUTUBE_MUSIC + "/youtubei/v1/search?prettyPrint=false",
                    body, headersFor(WEB_REMIX, true));
        } catch (Exception e) {
            throw new YtMusicTransportException("search(\"" + query + "\") falló: " + e.getMessage(), e);
        }
    }

    // ── Player (stream resolution) ────────────────────────────────────

    /**
     * Hits {@code /player}. {@code contentPot} (content-bound proof-of-origin
     * token) and {@code visitorData} are optional extras that, combined with
     * the ANDROID_VR client, unlock plain (non-signature-cipher) URLs.
     */
    /** Raw anonymous search suggestions call for the YouTube Music search box. */
    public JsonNode searchSuggestions(String query) {
        Map<String, Object> body = baseBody(WEB_REMIX);
        body.put("input", query);
        try {
            return post(ORIGIN_YOUTUBE_MUSIC + "/youtubei/v1/music/get_search_suggestions?prettyPrint=false",
                    body, headersFor(WEB_REMIX, true));
        } catch (Exception e) {
            throw new YtMusicTransportException("searchSuggestions(\"" + query + "\") fallo: " + e.getMessage(), e);
        }
    }

    public JsonNode player(YtMusicClient client, String videoId, String contentPot, String visitorData) {
        Map<String, Object> contextClient = buildClientContext(client);
        if (visitorData != null) {
            contextClient.put("visitorData", visitorData);
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("client", contextClient);
        context.put("user", Map.of("lockedSafetyMode", false));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("context", context);
        body.put("videoId", videoId);
        body.put("contentCheckOk", true);
        body.put("racyCheckOk", true);
        if (contentPot != null) {
            body.put("serviceIntegrityDimensions", Map.of("poToken", contentPot));
        }
        if (client.isEmbedded()) {
            context.put("thirdParty", Map.of("embedUrl", "https://www.youtube.com/watch?v=" + videoId));
        }

        boolean isWeb = client.clientName().startsWith("WEB");
        String host = client.clientName().equals("WEB_REMIX") ? ORIGIN_YOUTUBE_MUSIC : WWW_YOUTUBE;
        String url = host + "/youtubei/v1/player?prettyPrint=false";
        try {
            return post(url, body, headersFor(client, isWeb));
        } catch (Exception e) {
            throw new YtMusicTransportException(client.clientName() + " /player falló: " + e.getMessage(), e);
        }
    }

    // ── Mix / radio ───────────────────────────────────────────────────

    /** {@code /next} with a seed video — returns the auto-generated radio queue. Works anonymously for public videos. */
    public JsonNode next(Map<String, Object> body) {
        try {
            return post(ORIGIN_YOUTUBE_MUSIC + "/youtubei/v1/next?prettyPrint=false",
                    body, headersFor(WEB_REMIX, true));
        } catch (Exception e) {
            throw new YtMusicTransportException("next() falló: " + e.getMessage(), e);
        }
    }

    // ── Visitor data ──────────────────────────────────────────────────

    /** Lightweight call to mint a fresh {@code visitorData} token, reused across {@code /player} calls in the process. */
    public String fetchVisitorData() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("context", Map.of("client", buildClientContext(WEB_REMIX)));
        try {
            JsonNode resp = post(ORIGIN_YOUTUBE_MUSIC + "/youtubei/v1/visitor_id?prettyPrint=false",
                    body, headersFor(WEB_REMIX, true));
            JsonNode visitor = resp.path("responseContext").path("visitorData");
            if (visitor.isMissingNode() || visitor.isNull()) {
                throw new YtMusicTransportException("visitor_id no devolvió visitorData");
            }
            return visitor.asText();
        } catch (YtMusicTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new YtMusicTransportException("visitor_id falló: " + e.getMessage(), e);
        }
    }

    /** Helper consumed by {@link YtMusicSearchService} and friends to build the client context for ad-hoc bodies. */
    public Map<String, Object> baseBodyFor(YtMusicClient client) {
        return baseBody(client);
    }
}
