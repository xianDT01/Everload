package com.everload.everload.service;

import com.everload.everload.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.List;

import static com.everload.everload.service.YtMusicJsonUtils.*;

/**
 * Discover/browse surfaces: the YouTube Music home feed (and its
 * continuation pages), album pages and artist pages. All anonymous —
 * {@code /browse} returns generic recommendations and full public
 * catalogue data without any cookie.
 *
 * <p>YT's browse responses are deeply polymorphic: every shelf, header and
 * row is keyed by which renderer it is, and column order shifts across
 * A/B-tested layouts. Every lookup below iterates and dispatches on the
 * renderer key / endpoint type — never on a fixed array index — mirroring
 * the structure that was verified end-to-end against live responses.
 */
@Service
public class YtMusicDiscoverService {

    private static final String TITLE_NODE = "title";
    private static final String HEADER_NODE = "header";
    private static final String CONTENTS_NODE = "contents";
    private static final String SUBTITLE_NODE = "subtitle";
    private static final String THUMBNAIL_NODE = "thumbnail";
    private static final String THUMBNAILS_NODE = "thumbnails";
    private static final String NAVIGATION_ENDPOINT = "navigationEndpoint";
    private static final String BROWSE_ENDPOINT = "browseEndpoint";
    private static final String BROWSE_ID_NODE = "browseId";
    private static final String WATCH_ENDPOINT = "watchEndpoint";
    private static final String VIDEO_ID_NODE = "videoId";
    private static final String PLAYLIST_ID_NODE = "playlistId";
    private static final String TAB_RENDERER = "tabRenderer";
    private static final String CONTENT_NODE = "content";
    private static final String SECTION_LIST_RENDERER = "sectionListRenderer";
    private static final String SINGLE_COLUMN_RENDERER = "singleColumnBrowseResultsRenderer";
    private static final String MUSIC_THUMBNAIL_RENDERER = "musicThumbnailRenderer";

    private final YtMusicInnertubeClient client;
    private YtMusicCache<String, YtDiscoverHomeDto> homeCache;
    private YtMusicCache<String, YtDiscoverHomeDto> newReleasesCache;
    private YtMusicCache<String, YtDiscoverHomeDto> chartsCache;
    private YtMusicCache<String, YtDiscoverHomeDto> moodsCache;
    private YtMusicCache<String, YtAlbumDto> albumCache;
    private YtMusicCache<String, YtArtistDto> artistCache;

    @Value("${ytmusic.cache.ttl-seconds:600}")
    private long cacheTtlSeconds;

    @Value("${ytmusic.cache.max-entries:200}")
    private int cacheMaxEntries;

    public YtMusicDiscoverService(YtMusicInnertubeClient client) {
        this.client = client;
    }

    @PostConstruct
    void init() {
        long ttlMillis = cacheTtlSeconds * 1000;
        homeCache = new YtMusicCache<>(ttlMillis, cacheMaxEntries);
        newReleasesCache = new YtMusicCache<>(ttlMillis, cacheMaxEntries);
        chartsCache = new YtMusicCache<>(ttlMillis, cacheMaxEntries);
        moodsCache = new YtMusicCache<>(ttlMillis, cacheMaxEntries);
        albumCache = new YtMusicCache<>(ttlMillis, cacheMaxEntries);
        artistCache = new YtMusicCache<>(ttlMillis, cacheMaxEntries);
    }

    // ── Home feed ─────────────────────────────────────────────────────

    public YtDiscoverHomeDto fetchHome() {
        return homeCache.getOrCompute("home", () -> parseInitial(client.browse("FEmusic_home")));
    }

    public YtDiscoverHomeDto fetchNewReleases() {
        return newReleasesCache.getOrCompute("new_releases",
                () -> parseInitial(client.browse("FEmusic_new_releases")));
    }

    public YtDiscoverHomeDto fetchCharts() {
        return chartsCache.getOrCompute("charts",
                () -> parseInitial(client.browse("FEmusic_charts")));
    }

    // ── Moods & genres ────────────────────────────────────────────────

    /**
     * Mood/genre catalogue ({@code FEmusic_moods_and_genres}): grids of
     * {@code musicNavigationButtonRenderer} buttons, one per mood, each
     * carrying the (browseId, params) pair its category page needs.
     */
    public YtDiscoverHomeDto fetchMoods() {
        return moodsCache.getOrCompute("moods",
                () -> parseMoods(client.browse("FEmusic_moods_and_genres")));
    }

    /** One mood/genre category page: carousels plus playlist grids. */
    public YtDiscoverHomeDto fetchMoodCategory(String browseId, String params) {
        String effectiveBrowseId = (browseId == null || browseId.isBlank())
                ? "FEmusic_moods_and_genres_category" : browseId;
        return moodsCache.getOrCompute("cat:" + effectiveBrowseId + ":" + params,
                () -> parseMoodCategory(client.browse(effectiveBrowseId, params)));
    }

    private YtDiscoverHomeDto parseMoods(JsonNode resp) {
        List<YtDiscoverShelfDto> shelves = new ArrayList<>();
        for (JsonNode section : albumSectionContents(resp)) {
            YtDiscoverShelfDto shelf = parseMoodShelf(section);
            if (shelf != null) shelves.add(shelf);
        }
        return YtDiscoverHomeDto.builder().shelves(shelves).build();
    }

    private YtDiscoverShelfDto parseMoodShelf(JsonNode section) {
        JsonNode grid = section.get("gridRenderer");
        if (grid == null) return null;
        String title = runsText(at(grid, HEADER_NODE, "gridHeaderRenderer"), TITLE_NODE, "runs");
        List<YtDiscoverItemDto> items = new ArrayList<>();
        JsonNode gridItems = grid.get("items");
        if (gridItems != null && gridItems.isArray()) {
            for (JsonNode gridItem : gridItems) {
                YtDiscoverItemDto item = parseMoodButton(gridItem);
                if (item != null) items.add(item);
            }
        }
        if (items.isEmpty()) return null;
        return YtDiscoverShelfDto.builder()
                .title(title == null ? "Moods" : title)
                .items(items)
                .build();
    }

    private YtDiscoverItemDto parseMoodButton(JsonNode gridItem) {
        JsonNode button = gridItem.get("musicNavigationButtonRenderer");
        if (button == null) return null;
        String label = runsText(button, "buttonText", "runs");
        String browseId = textAt(button, "clickCommand", BROWSE_ENDPOINT, BROWSE_ID_NODE);
        if (label == null || browseId == null) return null;
        return YtDiscoverItemDto.builder()
                .type(YtDiscoverItemDto.Type.MOOD)
                .title(label)
                .moodBrowseId(browseId)
                .moodParams(textAt(button, "clickCommand", BROWSE_ENDPOINT, "params"))
                .build();
    }

    private YtDiscoverHomeDto parseMoodCategory(JsonNode resp) {
        List<YtDiscoverShelfDto> shelves = new ArrayList<>();
        for (JsonNode section : albumSectionContents(resp)) {
            YtDiscoverShelfDto shelf = parseShelf(section);
            if (shelf == null) shelf = parseGridShelf(section);
            if (shelf != null) shelves.add(shelf);
        }
        return YtDiscoverHomeDto.builder().shelves(shelves).build();
    }

    private YtDiscoverShelfDto parseGridShelf(JsonNode section) {
        JsonNode grid = section.get("gridRenderer");
        if (grid == null) return null;
        String title = runsText(at(grid, HEADER_NODE, "gridHeaderRenderer"), TITLE_NODE, "runs");
        List<YtDiscoverItemDto> items = new ArrayList<>();
        JsonNode gridItems = grid.get("items");
        if (gridItems != null && gridItems.isArray()) {
            for (JsonNode tile : gridItems) {
                YtDiscoverItemDto item = parseTile(tile);
                if (item != null) items.add(item);
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        String shelfTitle = title == null ? "" : title;
        return YtDiscoverShelfDto.builder().title(shelfTitle).items(items).build();
    }

    public YtDiscoverHomeDto fetchContinuation(String token) {
        // Continuation pages are inherently positional (page N depends on
        // page N-1's token) — caching by token still dedups repeat clicks.
        return homeCache.getOrCompute("cont:" + token,
                () -> parseContinuation(client.browseContinuation(token)));
    }

    private YtDiscoverHomeDto parseInitial(JsonNode resp) {
        List<JsonNode> sections = tabSectionContents(resp,
                CONTENTS_NODE, SINGLE_COLUMN_RENDERER, "tabs");
        String continuation = null;
        JsonNode tabs = at(resp, CONTENTS_NODE, SINGLE_COLUMN_RENDERER, "tabs");
        if (tabs.isArray()) {
            for (JsonNode tab : tabs) {
                String token = firstContinuation(at(tab, TAB_RENDERER, CONTENT_NODE,
                        SECTION_LIST_RENDERER, "continuations"));
                if (token != null) {
                    continuation = token;
                    break;
                }
            }
        }
        List<YtDiscoverShelfDto> shelves = new ArrayList<>();
        for (JsonNode s : sections) {
            YtDiscoverShelfDto shelf = parseShelf(s);
            if (shelf != null) shelves.add(shelf);
        }
        return YtDiscoverHomeDto.builder().shelves(shelves).continuation(continuation).build();
    }

    private YtDiscoverHomeDto parseContinuation(JsonNode resp) {
        JsonNode contents = at(resp, "continuationContents", "sectionListContinuation", CONTENTS_NODE);
        String continuation = firstContinuation(at(resp, "continuationContents",
                "sectionListContinuation", "continuations"));
        List<YtDiscoverShelfDto> shelves = new ArrayList<>();
        if (contents.isArray()) {
            for (JsonNode s : contents) {
                YtDiscoverShelfDto shelf = parseShelf(s);
                if (shelf != null) shelves.add(shelf);
            }
        }
        return YtDiscoverHomeDto.builder().shelves(shelves).continuation(continuation).build();
    }

    private YtDiscoverShelfDto parseShelf(JsonNode section) {
        JsonNode shelf = section.get("musicCarouselShelfRenderer");
        if (shelf == null) {
            return null;
        }
        JsonNode header = at(shelf, HEADER_NODE, "musicCarouselShelfBasicHeaderRenderer");
        String title = runsText(header, TITLE_NODE, "runs");
        if (title == null) {
            return null;
        }
        String strapline = runsText(header, "strapline", "runs");
        String moreBrowseId = textAt(header, "moreContentButton", "buttonRenderer",
                NAVIGATION_ENDPOINT, BROWSE_ENDPOINT, BROWSE_ID_NODE);

        List<YtDiscoverItemDto> items = new ArrayList<>();
        JsonNode contents = shelf.get(CONTENTS_NODE);
        if (contents != null && contents.isArray()) {
            for (JsonNode tile : contents) {
                YtDiscoverItemDto item = parseTile(tile);
                if (item != null) items.add(item);
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        return YtDiscoverShelfDto.builder()
                .title(title).strapline(strapline).moreBrowseId(moreBrowseId).items(items).build();
    }

    private YtDiscoverItemDto parseTile(JsonNode item) {
        JsonNode r = item.get("musicTwoRowItemRenderer");
        if (r == null) {
            return null;
        }
        String title = runsText(r, TITLE_NODE, "runs");
        if (title == null) {
            return null;
        }
        String subtitle = runsText(r, SUBTITLE_NODE, "runs");
        if (subtitle == null) subtitle = "";
        String thumbnail = normalizeThumbnail(bestThumbnailAt(r,
                "thumbnailRenderer", MUSIC_THUMBNAIL_RENDERER, THUMBNAIL_NODE, THUMBNAILS_NODE));

        String videoId = textAt(r, NAVIGATION_ENDPOINT, WATCH_ENDPOINT, VIDEO_ID_NODE);
        if (videoId != null) {
            return YtDiscoverItemDto.builder()
                    .type(YtDiscoverItemDto.Type.SONG)
                    .title(title).subtitle(subtitle).thumbnailUrl(thumbnail)
                    .track(buildSongTrack(videoId, title, subtitle, thumbnail))
                    .build();
        }

        String playlistId = textAt(r, NAVIGATION_ENDPOINT, "watchPlaylistEndpoint", PLAYLIST_ID_NODE);
        if (playlistId != null) {
            return playlistItem(playlistId, title, subtitle, thumbnail);
        }

        String browseId = textAt(r, NAVIGATION_ENDPOINT, BROWSE_ENDPOINT, BROWSE_ID_NODE);
        if (browseId != null) {
            if (browseId.startsWith("VL")) {
                return playlistItem(browseId.substring(2), title, subtitle, thumbnail);
            }
            if (browseId.startsWith("MPRE")) {
                return YtDiscoverItemDto.builder()
                        .type(YtDiscoverItemDto.Type.ALBUM)
                        .browseId(browseId).title(title).subtitle(subtitle).thumbnailUrl(thumbnail)
                        .build();
            }
            if (browseId.startsWith("UC")) {
                return YtDiscoverItemDto.builder()
                        .type(YtDiscoverItemDto.Type.ARTIST)
                        .channelId(browseId).title(title).thumbnailUrl(thumbnail)
                        .build();
            }
            if (browseId.startsWith("FEmusic_")) {
                return YtDiscoverItemDto.builder()
                        .type(YtDiscoverItemDto.Type.MOOD)
                        .moodBrowseId(browseId).title(title).thumbnailUrl(thumbnail)
                        .build();
            }
        }
        return null;
    }

    private YtDiscoverItemDto playlistItem(String playlistId, String title, String subtitle, String thumbnail) {
        return YtDiscoverItemDto.builder()
                .type(YtDiscoverItemDto.Type.PLAYLIST)
                .playlistId(playlistId).title(title).subtitle(subtitle).thumbnailUrl(thumbnail)
                .build();
    }

    private YtTrackDto buildSongTrack(String videoId, String title, String subtitle, String thumbnail) {
        // Subtitle for songs/videos is "Artist • N views" — the first
        // segment is the artist; everything after belongs to metadata we drop.
        String primaryArtist = subtitle == null ? "" : subtitle.split("•", 2)[0].trim();
        return YtTrackDto.builder()
                .videoId(videoId)
                .title(title == null ? "" : title)
                .artist(primaryArtist)
                .artists(primaryArtist.isBlank() ? List.of() : List.of(primaryArtist))
                .album("")
                .albumId(synthesizeAlbumId("", primaryArtist))
                .durationSeconds(0)
                .thumbnailUrl(thumbnail)
                .build();
    }

    /** Every {@code tabs[].tabRenderer.content.sectionListRenderer.contents} reachable from a tabs root. */
    private List<JsonNode> tabSectionContents(JsonNode resp, String... tabsPath) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode tabs = at(resp, tabsPath);
        if (!tabs.isArray()) {
            return out;
        }
        for (JsonNode tab : tabs) {
            JsonNode contents = at(tab, TAB_RENDERER, CONTENT_NODE, SECTION_LIST_RENDERER, CONTENTS_NODE);
            if (contents.isArray()) {
                contents.forEach(out::add);
            }
        }
        return out;
    }

    // ── Album ─────────────────────────────────────────────────────────

    public YtAlbumDto fetchAlbum(String browseId) {
        return albumCache.getOrCompute(browseId, () -> parseAlbum(browseId, client.browse(browseId)));
    }

    public List<YtTrackDto> fetchAlbumTracks(String browseId) {
        return fetchAlbum(browseId).getTracks();
    }

    private YtAlbumDto parseAlbum(String browseId, JsonNode resp) {
        List<JsonNode> sections = albumSectionContents(resp);
        JsonNode header = findAlbumHeader(resp, sections);

        String title = runsText(header, TITLE_NODE, "runs");
        if (title == null) title = "";
        String artist = pickAlbumArtist(header);
        String year = pickAlbumYear(header);
        String thumbnail = normalizeThumbnail(bestAlbumThumbnail(header));

        List<YtTrackDto> tracks = new ArrayList<>();
        for (JsonNode section : sections) {
            JsonNode items = at(section, "musicShelfRenderer", CONTENTS_NODE);
            if (!items.isArray()) continue;
            for (JsonNode item : items) {
                JsonNode row = item.get("musicResponsiveListItemRenderer");
                if (row == null) continue;
                YtTrackDto track = parseAlbumRow(row, title, artist, thumbnail);
                if (track != null) tracks.add(track);
            }
        }

        return YtAlbumDto.builder()
                .browseId(browseId).title(title).artist(artist).year(year)
                .thumbnailUrl(thumbnail).tracks(tracks)
                .build();
    }

    /** Merges {@code tabs[].sectionListRenderer.contents} with {@code secondaryContents} (two-column layout). */
    private List<JsonNode> albumSectionContents(JsonNode resp) {
        List<JsonNode> out = new ArrayList<>();
        for (String root : new String[]{"twoColumnBrowseResultsRenderer", SINGLE_COLUMN_RENDERER}) {
            JsonNode tabs = at(resp, CONTENTS_NODE, root, "tabs");
            if (tabs.isArray()) {
                for (JsonNode tab : tabs) {
                    JsonNode contents = at(tab, TAB_RENDERER, CONTENT_NODE, SECTION_LIST_RENDERER, CONTENTS_NODE);
                    if (contents.isArray()) contents.forEach(out::add);
                }
            }
        }
        JsonNode secondary = at(resp, CONTENTS_NODE, "twoColumnBrowseResultsRenderer",
                "secondaryContents", SECTION_LIST_RENDERER, CONTENTS_NODE);
        if (secondary.isArray()) secondary.forEach(out::add);
        return out;
    }

    private JsonNode findAlbumHeader(JsonNode resp, List<JsonNode> sections) {
        for (JsonNode s : sections) {
            if (s.has("musicResponsiveHeaderRenderer")) return s.get("musicResponsiveHeaderRenderer");
        }
        for (JsonNode s : sections) {
            if (s.has("musicDetailHeaderRenderer")) return s.get("musicDetailHeaderRenderer");
        }
        JsonNode headerObj = resp.get(HEADER_NODE);
        if (headerObj != null && headerObj.isObject()) {
            var fields = headerObj.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (entry.getKey().endsWith("HeaderRenderer")) {
                    return entry.getValue();
                }
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static final java.util.Set<String> ALBUM_KIND_LABELS =
            java.util.Set.of("Album", "Single", "EP", "Song", "Video", "Audio", "Playlist");

    private String pickAlbumArtist(JsonNode header) {
        JsonNode straplineRuns = at(header, "straplineTextOne", "runs");
        if (straplineRuns.isArray()) {
            for (JsonNode r : straplineRuns) {
                String t = textOf(r);
                if (t != null && !t.isBlank() && !t.equals("•")) {
                    return t;
                }
            }
        }
        JsonNode subtitleRuns = at(header, SUBTITLE_NODE, "runs");
        if (subtitleRuns.isArray()) {
            for (JsonNode r : subtitleRuns) {
                String t = textOf(r);
                if (isAlbumArtistLabel(t)) return t.trim();
            }
        }
        return null;
    }

    private boolean isAlbumArtistLabel(String value) {
        if (value == null) return false;
        String label = value.trim();
        return !label.isEmpty() && !label.equals("•")
                && !(label.length() == 4 && label.chars().allMatch(Character::isDigit))
                && !ALBUM_KIND_LABELS.contains(label);
    }

    private String pickAlbumYear(JsonNode header) {
        for (String key : new String[]{SUBTITLE_NODE, "secondSubtitle"}) {
            JsonNode runs = at(header, key, "runs");
            if (runs.isArray()) {
                for (JsonNode r : runs) {
                    String year = normalizeYear(textOf(r));
                    if (year != null) return year;
                }
            }
        }
        return null;
    }

    private String normalizeYear(String value) {
        if (value == null) return null;
        String candidate = value.trim();
        return candidate.length() == 4 && candidate.chars().allMatch(Character::isDigit)
                ? candidate : null;
    }

    private String bestAlbumThumbnail(JsonNode header) {
        for (String renderer : new String[]{MUSIC_THUMBNAIL_RENDERER, "croppedSquareThumbnailRenderer"}) {
            String url = bestThumbnailAt(header, THUMBNAIL_NODE, renderer, THUMBNAIL_NODE, THUMBNAILS_NODE);
            if (url != null) return url;
        }
        return null;
    }

    private YtTrackDto parseAlbumRow(JsonNode row, String albumTitle, String albumArtist, String albumThumbnail) {
        FlexColumnValues values = collectFlexColumnValues(row);
        String videoId = values.videoId;
        if (videoId == null) {
            videoId = textAt(row, "playlistItemData", VIDEO_ID_NODE);
        }
        if (videoId == null || values.title.isEmpty()) {
            return null;
        }
        String primaryArtist = values.artist != null ? values.artist : albumArtist;
        if (primaryArtist == null) primaryArtist = "";
        int duration = fixedColumnsDuration(row);
        if (duration == 0 && values.duration != null) duration = values.duration;

        return YtTrackDto.builder()
                .videoId(videoId)
                .title(values.title)
                .artist(primaryArtist)
                .artists(primaryArtist.isBlank() ? List.of() : List.of(primaryArtist))
                .album(albumTitle == null ? "" : albumTitle)
                .albumId(synthesizeAlbumId(albumTitle, primaryArtist))
                .durationSeconds(duration)
                .thumbnailUrl(albumThumbnail)
                .build();
    }

    // ── Artist ────────────────────────────────────────────────────────

    public YtArtistDto fetchArtist(String channelId) {
        return artistCache.getOrCompute(channelId, () -> parseArtist(channelId, client.browse(channelId)));
    }

    private YtArtistDto parseArtist(String channelId, JsonNode resp) {
        JsonNode header = findArtistHeader(resp);
        String name = runsText(header, TITLE_NODE, "runs");
        String description = runsText(header, "description", "runs");
        String banner = bestArtistBanner(header);

        List<YtTrackDto> topSongs = new ArrayList<>();
        List<YtAlbumDto> albums = new ArrayList<>();
        List<YtAlbumDto> singles = new ArrayList<>();
        for (JsonNode section : albumSectionContents(resp)) {
            collectArtistSection(section, topSongs, albums, singles);
        }

        return YtArtistDto.builder()
                .channelId(channelId)
                .name(name == null ? "" : name)
                .description(description)
                .thumbnailUrl(banner)
                .topSongs(topSongs)
                .albums(albums)
                .singles(singles)
                .build();
    }

    private void collectArtistSection(JsonNode section, List<YtTrackDto> topSongs,
                                      List<YtAlbumDto> albums, List<YtAlbumDto> singles) {
        JsonNode carousel = section.get("musicCarouselShelfRenderer");
        if (carousel != null) {
            String carouselTitle = safe(runsText(at(carousel, HEADER_NODE,
                    "musicCarouselShelfBasicHeaderRenderer"), TITLE_NODE, "runs")).toLowerCase();
            boolean isSingles = carouselTitle.contains("single") || carouselTitle.contains("sencillo");
            collectCarouselAlbums(carousel, isSingles ? singles : albums);
            return;
        }
        JsonNode shelf = section.get("musicShelfRenderer");
        if (shelf == null || !"Top songs".equalsIgnoreCase(safe(runsText(shelf, TITLE_NODE, "runs")))) {
            return;
        }
        JsonNode items = shelf.get(CONTENTS_NODE);
        if (items == null || !items.isArray()) return;
        for (JsonNode item : items) {
            JsonNode row = item.get("musicResponsiveListItemRenderer");
            if (row == null) continue;
            YtTrackDto track = parseArtistSongRow(row);
            if (track != null) topSongs.add(track);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void collectCarouselAlbums(JsonNode carousel, List<YtAlbumDto> out) {
        JsonNode contents = carousel.get(CONTENTS_NODE);
        if (contents == null || !contents.isArray()) return;
        for (JsonNode tile : contents) {
            YtAlbumDto album = parseCarouselAlbum(tile);
            if (album != null) out.add(album);
        }
    }

    private YtAlbumDto parseCarouselAlbum(JsonNode tile) {
        JsonNode renderer = tile.get("musicTwoRowItemRenderer");
        if (renderer == null) return null;
        String browseId = textAt(renderer, NAVIGATION_ENDPOINT, BROWSE_ENDPOINT, BROWSE_ID_NODE);
        if (browseId == null || !browseId.startsWith("MPRE")) return null;
        String title = runsText(renderer, TITLE_NODE, "runs");
        return YtAlbumDto.builder()
                .browseId(browseId)
                .title(title == null ? "" : title)
                .artist(runsText(renderer, SUBTITLE_NODE, "runs"))
                .thumbnailUrl(normalizeThumbnail(bestThumbnailAt(renderer,
                        "thumbnailRenderer", MUSIC_THUMBNAIL_RENDERER, THUMBNAIL_NODE, THUMBNAILS_NODE)))
                .tracks(List.of())
                .build();
    }

    private JsonNode findArtistHeader(JsonNode resp) {
        JsonNode immersive = at(resp, HEADER_NODE, "musicImmersiveHeaderRenderer");
        if (!immersive.isMissingNode()) return immersive;
        JsonNode visual = at(resp, HEADER_NODE, "musicVisualHeaderRenderer");
        if (!visual.isMissingNode()) return visual;
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private String bestArtistBanner(JsonNode header) {
        for (String renderer : new String[]{THUMBNAIL_NODE, "foregroundThumbnail"}) {
            String url = bestThumbnailAt(header, renderer, MUSIC_THUMBNAIL_RENDERER, THUMBNAIL_NODE, THUMBNAILS_NODE);
            if (url != null) return normalizeThumbnail(url);
        }
        return null;
    }

    private YtTrackDto parseArtistSongRow(JsonNode row) {
        FlexColumnValues values = collectFlexColumnValues(row);
        String videoId = values.videoId;
        if (videoId == null) {
            videoId = textAt(row, "playlistItemData", VIDEO_ID_NODE);
        }
        if (videoId == null || values.title.isEmpty()) {
            return null;
        }
        int duration = fixedColumnsDuration(row);
        if (duration == 0 && values.duration != null) duration = values.duration;
        String artist = values.artist == null ? "" : values.artist;
        String album = values.album == null ? "" : values.album;
        String thumbnail = normalizeThumbnail(bestThumbnailAt(row,
                THUMBNAIL_NODE, MUSIC_THUMBNAIL_RENDERER, THUMBNAIL_NODE, THUMBNAILS_NODE));

        return YtTrackDto.builder()
                .videoId(videoId)
                .title(values.title)
                .artist(artist)
                .artists(artist.isBlank() ? List.of() : List.of(artist))
                .album(album)
                .albumId(synthesizeAlbumId(album, artist))
                .durationSeconds(duration)
                .thumbnailUrl(thumbnail)
                .build();
    }

    // ── Flex-column classification (shared by album & artist row parsing) ──

    private record FlexColumn(Kind kind, String text, String videoId, String playlistId, int durationSecs) {
        enum Kind { TITLE, ARTIST, ALBUM, DURATION, PLAY_COUNT, OTHER, EMPTY }
    }

    private static final class FlexColumnValues {
        private String videoId;
        private String title = "";
        private String artist;
        private String album;
        private Integer duration;

        private void accept(FlexColumn column) {
            switch (column.kind()) {
                case TITLE -> {
                    if (title.isEmpty()) title = column.text();
                    if (videoId == null && column.videoId() != null) videoId = column.videoId();
                }
                case ARTIST -> { if (artist == null) artist = column.text(); }
                case ALBUM -> { if (album == null) album = column.text(); }
                case DURATION -> { if (duration == null) duration = column.durationSecs(); }
                default -> {
                    // Other column kinds do not contribute to track metadata.
                }
            }
        }
    }

    private FlexColumnValues collectFlexColumnValues(JsonNode row) {
        FlexColumnValues values = new FlexColumnValues();
        for (FlexColumn column : classifyFlexColumns(row)) {
            values.accept(column);
        }
        return values;
    }

    /**
     * Classifies each flexColumn on a {@code musicResponsiveListItemRenderer}
     * by what it actually carries (endpoint type / text shape) — never by
     * position. The artist "Top songs" shelf orders columns
     * title/artist/play-count/album, not the usual title/artist/album, so a
     * positional read silently mis-tags or drops fields.
     */
    private List<FlexColumn> classifyFlexColumns(JsonNode row) {
        List<FlexColumn> out = new ArrayList<>();
        JsonNode cols = row.get("flexColumns");
        if (cols == null || !cols.isArray()) {
            return out;
        }
        for (JsonNode col : cols) {
            out.add(classifyFlexColumn(col));
        }
        return out;
    }

    private FlexColumn classifyFlexColumn(JsonNode column) {
        JsonNode runs = at(column, "musicResponsiveListItemFlexColumnRenderer", "text", "runs");
        if (!runs.isArray() || runs.isEmpty()) return new FlexColumn(FlexColumn.Kind.EMPTY, "", null, null, 0);
        StringBuilder combinedText = new StringBuilder();
        for (JsonNode run : runs) {
            JsonNode textNode = run.get("text");
            if (textNode != null && textNode.isTextual()) combinedText.append(textNode.asText());
        }
        String text = combinedText.toString();
        if (text.isBlank()) return new FlexColumn(FlexColumn.Kind.EMPTY, "", null, null, 0);

        JsonNode firstNavigation = runs.get(0).get(NAVIGATION_ENDPOINT);
        String videoId = firstNavigation == null ? null : textAt(firstNavigation, WATCH_ENDPOINT, VIDEO_ID_NODE);
        if (videoId != null) {
            String playlistId = textAt(firstNavigation, WATCH_ENDPOINT, PLAYLIST_ID_NODE);
            return new FlexColumn(FlexColumn.Kind.TITLE, text, videoId, playlistId, 0);
        }
        FlexColumn classified = classifyByEndpoint(runs, text);
        if (classified != null) return classified;
        int seconds = parseMmSs(text.trim());
        if (seconds > 0 || text.trim().matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
            return new FlexColumn(FlexColumn.Kind.DURATION, text, null, null, seconds);
        }
        FlexColumn.Kind kind = isPlayCountText(text) ? FlexColumn.Kind.PLAY_COUNT : FlexColumn.Kind.OTHER;
        return new FlexColumn(kind, text, null, null, 0);
    }

    private FlexColumn classifyByEndpoint(JsonNode runs, String text) {
        for (JsonNode r : runs) {
            JsonNode nav = r.get(NAVIGATION_ENDPOINT);
            if (nav == null) continue;
            String browseId = textAt(nav, BROWSE_ENDPOINT, BROWSE_ID_NODE);
            FlexColumn endpointColumn = classifyBrowseEndpoint(browseId, text);
            if (endpointColumn != null) return endpointColumn;
            String playlistId = textAt(nav, "watchPlaylistEndpoint", PLAYLIST_ID_NODE);
            if (playlistId == null) playlistId = textAt(nav, WATCH_ENDPOINT, PLAYLIST_ID_NODE);
            if (playlistId != null && playlistId.startsWith("OLAK5uy_")) {
                return new FlexColumn(FlexColumn.Kind.ALBUM, text, null, null, 0);
            }
        }
        return null;
    }

    private FlexColumn classifyBrowseEndpoint(String browseId, String text) {
        if (browseId == null) return null;
        if (browseId.startsWith("UC")) {
            return new FlexColumn(FlexColumn.Kind.ARTIST, text, null, null, 0);
        }
        if (browseId.startsWith("MPRE")) {
            return new FlexColumn(FlexColumn.Kind.ALBUM, text, null, null, 0);
        }
        return null;
    }

    private boolean isPlayCountText(String s) {
        String lower = s.toLowerCase();
        return lower.contains("play") || lower.contains("view") || lower.contains("listener");
    }

    /** Scans {@code fixedColumns} for the first cell whose runs parse as mm:ss. */
    private int fixedColumnsDuration(JsonNode row) {
        JsonNode cols = row.get("fixedColumns");
        if (cols == null || !cols.isArray()) {
            return 0;
        }
        for (JsonNode col : cols) {
            String text = runsText(col, "musicResponsiveListItemFixedColumnRenderer", "text", "runs");
            if (text != null) {
                int secs = parseMmSs(text.trim());
                if (secs > 0) return secs;
            }
        }
        return 0;
    }

    private String textOf(JsonNode run) {
        JsonNode text = run.get("text");
        return text != null && text.isTextual() ? text.asText() : null;
    }
}
