package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.YtPlaylistSummaryDto;
import com.EverLoad.everload.dto.YtTrackDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.EverLoad.everload.service.YtMusicJsonUtils.*;

/**
 * Public-playlist browsing. Anonymous {@code /browse} on a {@code VL<id>}
 * browse id returns a playlist's metadata and track list whenever the
 * playlist is public — there is no library/ownership concept here, only
 * reading what anyone with the link could see.
 */
@Service
public class YtMusicPlaylistService {

    /** Hard cap on continuation pages per playlist fetch — guards against pathological/looping tokens. */
    private static final int MAX_CONTINUATION_PAGES = 40;

    private final YtMusicInnertubeClient client;
    private YtMusicCache<String, YtPlaylistSummaryDto> summaryCache;
    private YtMusicCache<String, List<YtTrackDto>> entriesCache;

    @Value("${ytmusic.cache.ttl-seconds:600}")
    private long cacheTtlSeconds;

    @Value("${ytmusic.cache.max-entries:200}")
    private int cacheMaxEntries;

    public YtMusicPlaylistService(YtMusicInnertubeClient client) {
        this.client = client;
    }

    @PostConstruct
    void init() {
        long ttlMillis = cacheTtlSeconds * 1000;
        summaryCache = new YtMusicCache<>(ttlMillis, cacheMaxEntries);
        entriesCache = new YtMusicCache<>(ttlMillis, cacheMaxEntries);
    }

    private static String browseIdFor(String playlistId) {
        return playlistId.startsWith("VL") ? playlistId : "VL" + playlistId;
    }

    public YtPlaylistSummaryDto fetchPlaylistSummary(String playlistId) {
        return summaryCache.getOrCompute(playlistId,
                () -> parseSummary(playlistId, client.browse(browseIdFor(playlistId))));
    }

    public List<YtTrackDto> getPlaylistEntries(String playlistId) {
        return entriesCache.getOrCompute(playlistId, () -> fetchEntries(playlistId));
    }

    // ── Summary / header ──────────────────────────────────────────────

    private YtPlaylistSummaryDto parseSummary(String playlistId, JsonNode resp) {
        JsonNode header = findPlaylistHeader(resp);
        String title = runsText(header, "title", "runs");
        if (title == null) title = textAt(header, "title", "simpleText");
        String thumbnail = normalizeThumbnail(bestPlaylistThumbnail(header));
        return YtPlaylistSummaryDto.builder()
                .playlistId(playlistId)
                .title(title == null ? "" : title)
                .thumbnailUrl(thumbnail)
                .build();
    }

    private JsonNode findPlaylistHeader(JsonNode resp) {
        JsonNode direct = unwrapHeader(resp.get("header"));
        if (!direct.isMissingNode()) {
            return direct;
        }
        for (String root : new String[]{"twoColumnBrowseResultsRenderer", "singleColumnBrowseResultsRenderer"}) {
            JsonNode tabs = at(resp, "contents", root, "tabs");
            if (tabs.isArray()) {
                for (JsonNode tab : tabs) {
                    JsonNode contents = at(tab, "tabRenderer", "content", "sectionListRenderer", "contents");
                    if (contents.isArray()) {
                        for (JsonNode section : contents) {
                            for (String key : new String[]{"musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer"}) {
                                JsonNode h = section.get(key);
                                if (h != null) return h;
                            }
                        }
                    }
                }
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    /** Some layouts wrap the real header in an editable/legacy shell — peel it off. */
    private JsonNode unwrapHeader(JsonNode headerObj) {
        JsonNode renderer = firstKeyEndingWith(headerObj, "HeaderRenderer");
        if (renderer.isMissingNode()) {
            return renderer;
        }
        JsonNode nested = firstKeyEndingWith(renderer.get("header"), "HeaderRenderer");
        return nested.isMissingNode() ? renderer : nested;
    }

    private String bestPlaylistThumbnail(JsonNode header) {
        for (String renderer : new String[]{"musicThumbnailRenderer", "croppedSquareThumbnailRenderer"}) {
            String url = bestThumbnailAt(header, "thumbnail", renderer, "thumbnail", "thumbnails");
            if (url != null) return url;
        }
        return bestThumbnailAt(header, "thumbnail", "thumbnails");
    }

    // ── Track list (with continuation walking) ───────────────────────

    private List<YtTrackDto> fetchEntries(String playlistId) {
        JsonNode resp = client.browse(browseIdFor(playlistId));
        List<YtTrackDto> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        String continuation = walkPlaylistShelf(findPlaylistShelf(resp), out, seen);
        int pages = 0;
        while (continuation != null && pages++ < MAX_CONTINUATION_PAGES) {
            continuation = walkPlaylistContinuation(client.browseContinuation(continuation), out, seen);
        }
        return out;
    }

    private JsonNode findPlaylistShelf(JsonNode resp) {
        for (String root : new String[]{"twoColumnBrowseResultsRenderer", "singleColumnBrowseResultsRenderer"}) {
            JsonNode tabs = at(resp, "contents", root, "tabs");
            if (tabs.isArray()) {
                for (JsonNode tab : tabs) {
                    JsonNode contents = at(tab, "tabRenderer", "content", "sectionListRenderer", "contents");
                    JsonNode found = firstPlaylistShelfIn(contents);
                    if (found != null) return found;
                }
            }
        }
        JsonNode secondary = at(resp, "contents", "twoColumnBrowseResultsRenderer",
                "secondaryContents", "sectionListRenderer", "contents");
        JsonNode found = firstPlaylistShelfIn(secondary);
        return found != null ? found : com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private JsonNode firstPlaylistShelfIn(JsonNode sectionsArray) {
        if (!sectionsArray.isArray()) {
            return null;
        }
        for (JsonNode section : sectionsArray) {
            JsonNode shelf = section.get("musicPlaylistShelfRenderer");
            if (shelf != null) return shelf;
        }
        return null;
    }

    private String walkPlaylistShelf(JsonNode shelf, List<YtTrackDto> out, Set<String> seen) {
        if (shelf.isMissingNode()) {
            return null;
        }
        JsonNode contents = shelf.get("contents");
        if (contents != null && contents.isArray()) {
            for (JsonNode item : contents) {
                collectRow(item, out, seen);
            }
        }
        return firstContinuation(at(shelf, "continuations"));
    }

    private String walkPlaylistContinuation(JsonNode resp, List<YtTrackDto> out, Set<String> seen) {
        JsonNode shelfCont = at(resp, "continuationContents", "musicPlaylistShelfContinuation");
        if (shelfCont.isMissingNode()) {
            shelfCont = at(resp, "continuationContents", "playlistVideoListContinuation");
        }
        JsonNode contents = shelfCont.get("contents");
        if (contents != null && contents.isArray()) {
            for (JsonNode item : contents) {
                collectRow(item, out, seen);
            }
        }
        return firstContinuation(at(shelfCont, "continuations"));
    }

    private void collectRow(JsonNode item, List<YtTrackDto> out, Set<String> seen) {
        YtTrackDto track = parsePlaylistRow(item);
        if (track != null && seen.add(track.getVideoId())) {
            out.add(track);
        }
    }

    private YtTrackDto parsePlaylistRow(JsonNode item) {
        JsonNode row = item.get("musicResponsiveListItemRenderer");
        if (row == null) {
            return null;
        }
        String videoId = textAt(row, "playlistItemData", "videoId");
        if (videoId == null) {
            videoId = textAt(row, "flexColumns", "0", "musicResponsiveListItemFlexColumnRenderer",
                    "text", "runs", "0", "navigationEndpoint", "watchEndpoint", "videoId");
        }
        if (videoId == null) {
            return null;
        }
        String title = pickRun(row, 0, 0);
        String artist = pickRun(row, 1, 0);

        String albumText = pickRun(row, 2, 0);
        String album = albumText.isBlank() ? null : albumText;
        String albumBrowseId = textAt(row, "flexColumns", "2", "musicResponsiveListItemFlexColumnRenderer",
                "text", "runs", "0", "navigationEndpoint", "browseEndpoint", "browseId");
        if (albumBrowseId == null || !albumBrowseId.startsWith("MPRE")) {
            albumBrowseId = null;
        }

        int duration = parseMmSs(textAt(row, "fixedColumns", "0",
                "musicResponsiveListItemFixedColumnRenderer", "text", "runs", "0", "text"));
        String thumbnail = normalizeThumbnail(bestThumbnailAt(row, "thumbnail",
                "musicThumbnailRenderer", "thumbnail", "thumbnails"));
        String albumId = albumBrowseId != null ? "ytmusic:album:" + albumBrowseId : synthesizeAlbumId(album, artist);

        return YtTrackDto.builder()
                .videoId(videoId)
                .title(title == null ? "" : title)
                .artist(artist == null ? "" : artist)
                .artists(artist == null || artist.isBlank() ? List.of() : List.of(artist))
                .album(album == null ? "" : album)
                .albumId(albumId)
                .durationSeconds(duration)
                .thumbnailUrl(thumbnail)
                .build();
    }
}
