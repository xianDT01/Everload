package com.EverLoad.everload.service;

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
 * Free-text search against YouTube Music's public catalogue: merges the
 * "Top result", "Songs" and "Videos" tabs into one deduplicated list, plus
 * artist-name → channel-id resolution (used to open an artist page from a
 * track row that only carries a name).
 */
@Service
public class YtMusicSearchService {

    private final YtMusicInnertubeClient client;
    private YtMusicCache<String, List<YtTrackDto>> searchCache;

    @Value("${ytmusic.cache.ttl-seconds:600}")
    private long cacheTtlSeconds;

    @Value("${ytmusic.cache.max-entries:200}")
    private int cacheMaxEntries;

    public YtMusicSearchService(YtMusicInnertubeClient client) {
        this.client = client;
    }

    @PostConstruct
    void init() {
        searchCache = new YtMusicCache<>(cacheTtlSeconds * 1000, cacheMaxEntries);
    }

    public List<YtTrackDto> searchTracks(String query) {
        String key = query.trim().toLowerCase();
        return searchCache.getOrCompute(key, () -> doSearchTracks(query));
    }

    private List<YtTrackDto> doSearchTracks(String query) {
        List<YtTrackDto> top = walkTracks(client.search(query, null));
        List<YtTrackDto> songs = walkTracks(client.search(query, YtMusicInnertubeClient.SONGS_FILTER));
        List<YtTrackDto> videos = walkTracks(client.search(query, YtMusicInnertubeClient.VIDEOS_FILTER));

        List<YtTrackDto> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (YtTrackDto t : top) {
            pushUnique(t, out, seen);
        }
        int max = Math.max(songs.size(), videos.size());
        for (int i = 0; i < max; i++) {
            if (i < songs.size()) pushUnique(songs.get(i), out, seen);
            if (i < videos.size()) pushUnique(videos.get(i), out, seen);
        }
        return out;
    }

    private void pushUnique(YtTrackDto t, List<YtTrackDto> out, Set<String> seen) {
        if (t.getVideoId() != null && !t.getVideoId().isBlank() && seen.add(t.getVideoId())) {
            out.add(t);
        }
    }

    /** Resolve a free-text artist name to a {@code UC…} channel id, or null if no artist row matched. */
    public String resolveArtistChannelId(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        JsonNode resp = client.search(query, YtMusicInnertubeClient.ARTISTS_FILTER);
        return findFirstArtistBrowseId(resp);
    }

    private String findFirstArtistBrowseId(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode endpoint = node.get("browseEndpoint");
            if (endpoint != null) {
                String bid = textAt(endpoint, "browseId");
                if (bid != null && bid.startsWith("UC")) {
                    return bid;
                }
            }
            for (JsonNode child : node) {
                String found = findFirstArtistBrowseId(child);
                if (found != null) return found;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findFirstArtistBrowseId(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ── Shared row-walking (also used by playlist/album/mix parsing) ──

    List<YtTrackDto> walkTracks(JsonNode resp) {
        JsonNode shelves = at(resp, "contents", "tabbedSearchResultsRenderer", "tabs", "0",
                "tabRenderer", "content", "sectionListRenderer", "contents");
        if (!shelves.isArray()) {
            return List.of();
        }
        List<YtTrackDto> out = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (JsonNode shelf : shelves) {
            JsonNode card = shelf.get("musicCardShelfRenderer");
            if (card != null) {
                YtTrackDto fromCard = parseCardShelf(card);
                emit(fromCard, out, seenIds);
                JsonNode contents = card.get("contents");
                if (contents != null && contents.isArray()) {
                    for (JsonNode item : contents) {
                        emit(parseRow(item), out, seenIds);
                    }
                }
            }
            JsonNode shelfItems = at(shelf, "musicShelfRenderer", "contents");
            if (shelfItems.isArray()) {
                for (JsonNode item : shelfItems) {
                    emit(parseRow(item), out, seenIds);
                }
            }
        }
        return out;
    }

    private void emit(YtTrackDto t, List<YtTrackDto> out, Set<String> seenIds) {
        if (t != null && t.getVideoId() != null && seenIds.add(t.getVideoId())) {
            out.add(t);
        }
    }

    private YtTrackDto parseCardShelf(JsonNode card) {
        JsonNode endpoint = at(card, "onTap", "watchEndpoint");
        if (endpoint.isMissingNode()) {
            return null;
        }
        String videoId = textAt(endpoint, "videoId");
        String mvt = textAt(endpoint, "watchEndpointMusicSupportedConfigs",
                "watchEndpointMusicConfig", "musicVideoType");
        if (videoId == null || !isPlayableMusicVideoType(mvt)) {
            return null;
        }
        String title = textAt(card, "title", "runs", "0", "text");

        List<String> subtitle = new ArrayList<>();
        JsonNode subtitleRuns = at(card, "subtitle", "runs");
        if (subtitleRuns.isArray()) {
            for (JsonNode r : subtitleRuns) {
                JsonNode text = r.get("text");
                if (text != null && text.isTextual() && !isSeparator(text.asText())) {
                    subtitle.add(text.asText());
                }
            }
        }
        if (!subtitle.isEmpty()) {
            subtitle.remove(0); // drop the "Song"/"Video" kind label — mvt already encodes it
        }
        String artist = subtitle.isEmpty() ? "" : subtitle.get(0);
        String album = musicVideoTypeHasAlbum(mvt) && subtitle.size() > 1 ? subtitle.get(1) : null;
        String thumbnail = normalizeThumbnail(bestThumbnailAt(card, "thumbnail",
                "musicThumbnailRenderer", "thumbnail", "thumbnails"));

        return buildTrack(videoId, title, artist, album, null, 0, thumbnail);
    }

    private YtTrackDto parseRow(JsonNode item) {
        JsonNode row = item.get("musicResponsiveListItemRenderer");
        if (row == null) {
            return null;
        }
        String mvt = findMusicVideoType(row);
        if (!isPlayableMusicVideoType(mvt)) {
            return null;
        }
        String videoId = textAt(row, "playlistItemData", "videoId");
        if (videoId == null) {
            return null;
        }
        String thumbnail = normalizeThumbnail(bestThumbnailAt(row, "thumbnail",
                "musicThumbnailRenderer", "thumbnail", "thumbnails"));
        String title = pickRun(row, 0, 0);

        // Playlist-track rows carry duration in `fixedColumns`; plain search
        // rows pack everything (incl. duration) into flexColumns[1].
        if (row.has("fixedColumns")) {
            return parsePlaylistTrackRow(row, videoId, title, mvt, thumbnail);
        }
        return parseSearchRow(row, videoId, title, mvt, thumbnail);
    }

    private YtTrackDto parsePlaylistTrackRow(JsonNode row, String videoId, String title, String mvt, String thumbnail) {
        String artist = pickRun(row, 1, 0);
        String album = null;
        String albumBrowseId = null;
        if (musicVideoTypeHasAlbum(mvt)) {
            String a = pickRun(row, 2, 0);
            album = a.isEmpty() ? null : a;
            String bid = textAt(row, "flexColumns", "2", "musicResponsiveListItemFlexColumnRenderer",
                    "text", "runs", "0", "navigationEndpoint", "browseEndpoint", "browseId");
            albumBrowseId = (bid != null && bid.startsWith("MPRE")) ? bid : null;
        }
        int duration = parseMmSs(textAt(row, "fixedColumns", "0",
                "musicResponsiveListItemFixedColumnRenderer", "text", "runs", "0", "text"));
        return buildTrack(videoId, title, artist, album, albumBrowseId, duration, thumbnail);
    }

    private YtTrackDto parseSearchRow(JsonNode row, String videoId, String title, String mvt, String thumbnail) {
        // flex[1] runs (separators stripped):
        //   has-album rows:  [artist..., album, duration]
        //   video rows:      [artist..., view-count, duration]
        // Duration is always last; the slot before it is album OR view-count
        // depending on mvt — we dispatch on mvt rather than sniffing the text.
        List<String> tokens = new ArrayList<>(pickAllRuns(row, 1));
        int duration = tokens.isEmpty() ? 0 : parseMmSs(tokens.remove(tokens.size() - 1));
        String secondLast = tokens.isEmpty() ? null : tokens.remove(tokens.size() - 1);
        String album = musicVideoTypeHasAlbum(mvt) && secondLast != null && !secondLast.isBlank() ? secondLast : null;
        String artist = tokens.isEmpty() ? "" : tokens.get(0);
        return buildTrack(videoId, title, artist, album, null, duration, thumbnail);
    }

    private YtTrackDto buildTrack(String videoId, String title, String artist, String album,
                                  String albumBrowseId, int durationSeconds, String thumbnailUrl) {
        String albumId = albumBrowseId != null
                ? "ytmusic:album:" + albumBrowseId
                : synthesizeAlbumId(album, artist);
        return YtTrackDto.builder()
                .videoId(videoId)
                .title(title == null ? "" : title)
                .artist(artist == null ? "" : artist)
                .artists(artist == null || artist.isBlank() ? List.of() : List.of(artist))
                .album(album == null ? "" : album)
                .albumId(albumId)
                .durationSeconds(durationSeconds)
                .thumbnailUrl(thumbnailUrl)
                .build();
    }
}
