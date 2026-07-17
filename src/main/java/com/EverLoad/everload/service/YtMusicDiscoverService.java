package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.List;

import static com.EverLoad.everload.service.YtMusicJsonUtils.*;

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
            JsonNode grid = section.get("gridRenderer");
            if (grid == null) continue;
            String title = runsText(at(grid, "header", "gridHeaderRenderer"), "title", "runs");
            List<YtDiscoverItemDto> items = new ArrayList<>();
            JsonNode gridItems = grid.get("items");
            if (gridItems != null && gridItems.isArray()) {
                for (JsonNode gi : gridItems) {
                    JsonNode btn = gi.get("musicNavigationButtonRenderer");
                    if (btn == null) continue;
                    String label = runsText(btn, "buttonText", "runs");
                    String browseId = textAt(btn, "clickCommand", "browseEndpoint", "browseId");
                    String params = textAt(btn, "clickCommand", "browseEndpoint", "params");
                    if (label == null || browseId == null) continue;
                    items.add(YtDiscoverItemDto.builder()
                            .type(YtDiscoverItemDto.Type.MOOD)
                            .title(label)
                            .moodBrowseId(browseId)
                            .moodParams(params)
                            .build());
                }
            }
            if (!items.isEmpty()) {
                shelves.add(YtDiscoverShelfDto.builder()
                        .title(title == null ? "Moods" : title)
                        .items(items)
                        .build());
            }
        }
        return YtDiscoverHomeDto.builder().shelves(shelves).build();
    }

    private YtDiscoverHomeDto parseMoodCategory(JsonNode resp) {
        List<YtDiscoverShelfDto> shelves = new ArrayList<>();
        for (JsonNode section : albumSectionContents(resp)) {
            YtDiscoverShelfDto carousel = parseShelf(section);
            if (carousel != null) {
                shelves.add(carousel);
                continue;
            }
            JsonNode grid = section.get("gridRenderer");
            if (grid == null) continue;
            String title = runsText(at(grid, "header", "gridHeaderRenderer"), "title", "runs");
            List<YtDiscoverItemDto> items = new ArrayList<>();
            JsonNode gridItems = grid.get("items");
            if (gridItems != null && gridItems.isArray()) {
                for (JsonNode tile : gridItems) {
                    YtDiscoverItemDto item = parseTile(tile);
                    if (item != null) items.add(item);
                }
            }
            if (!items.isEmpty()) {
                shelves.add(YtDiscoverShelfDto.builder()
                        .title(title == null ? "" : title)
                        .items(items)
                        .build());
            }
        }
        return YtDiscoverHomeDto.builder().shelves(shelves).build();
    }

    public YtDiscoverHomeDto fetchContinuation(String token) {
        // Continuation pages are inherently positional (page N depends on
        // page N-1's token) — caching by token still dedups repeat clicks.
        return homeCache.getOrCompute("cont:" + token,
                () -> parseContinuation(client.browseContinuation(token)));
    }

    private YtDiscoverHomeDto parseInitial(JsonNode resp) {
        List<JsonNode> sections = tabSectionContents(resp,
                "contents", "singleColumnBrowseResultsRenderer", "tabs");
        String continuation = null;
        JsonNode tabs = at(resp, "contents", "singleColumnBrowseResultsRenderer", "tabs");
        if (tabs.isArray()) {
            for (JsonNode tab : tabs) {
                String token = firstContinuation(at(tab, "tabRenderer", "content",
                        "sectionListRenderer", "continuations"));
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
        JsonNode contents = at(resp, "continuationContents", "sectionListContinuation", "contents");
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
        JsonNode header = at(shelf, "header", "musicCarouselShelfBasicHeaderRenderer");
        String title = runsText(header, "title", "runs");
        if (title == null) {
            return null;
        }
        String strapline = runsText(header, "strapline", "runs");
        String moreBrowseId = textAt(header, "moreContentButton", "buttonRenderer",
                "navigationEndpoint", "browseEndpoint", "browseId");

        List<YtDiscoverItemDto> items = new ArrayList<>();
        JsonNode contents = shelf.get("contents");
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
        String title = runsText(r, "title", "runs");
        if (title == null) {
            return null;
        }
        String subtitle = runsText(r, "subtitle", "runs");
        if (subtitle == null) subtitle = "";
        String thumbnail = normalizeThumbnail(bestThumbnailAt(r,
                "thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails"));

        String videoId = textAt(r, "navigationEndpoint", "watchEndpoint", "videoId");
        if (videoId != null) {
            return YtDiscoverItemDto.builder()
                    .type(YtDiscoverItemDto.Type.SONG)
                    .title(title).subtitle(subtitle).thumbnailUrl(thumbnail)
                    .track(buildSongTrack(videoId, title, subtitle, thumbnail))
                    .build();
        }

        String playlistId = textAt(r, "navigationEndpoint", "watchPlaylistEndpoint", "playlistId");
        if (playlistId != null) {
            return playlistItem(playlistId, title, subtitle, thumbnail);
        }

        String browseId = textAt(r, "navigationEndpoint", "browseEndpoint", "browseId");
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
            JsonNode contents = at(tab, "tabRenderer", "content", "sectionListRenderer", "contents");
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

        String title = runsText(header, "title", "runs");
        if (title == null) title = "";
        String artist = pickAlbumArtist(header);
        String year = pickAlbumYear(header);
        String thumbnail = normalizeThumbnail(bestAlbumThumbnail(header));

        List<YtTrackDto> tracks = new ArrayList<>();
        for (JsonNode section : sections) {
            JsonNode items = at(section, "musicShelfRenderer", "contents");
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
        for (String root : new String[]{"twoColumnBrowseResultsRenderer", "singleColumnBrowseResultsRenderer"}) {
            JsonNode tabs = at(resp, "contents", root, "tabs");
            if (tabs.isArray()) {
                for (JsonNode tab : tabs) {
                    JsonNode contents = at(tab, "tabRenderer", "content", "sectionListRenderer", "contents");
                    if (contents.isArray()) contents.forEach(out::add);
                }
            }
        }
        JsonNode secondary = at(resp, "contents", "twoColumnBrowseResultsRenderer",
                "secondaryContents", "sectionListRenderer", "contents");
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
        JsonNode headerObj = resp.get("header");
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
        JsonNode subtitleRuns = at(header, "subtitle", "runs");
        if (subtitleRuns.isArray()) {
            for (JsonNode r : subtitleRuns) {
                String t = textOf(r);
                if (t == null) continue;
                t = t.trim();
                if (t.isEmpty() || t.equals("•")) continue;
                if (t.length() == 4 && t.chars().allMatch(Character::isDigit)) continue;
                if (ALBUM_KIND_LABELS.contains(t)) continue;
                return t;
            }
        }
        return null;
    }

    private String pickAlbumYear(JsonNode header) {
        for (String key : new String[]{"subtitle", "secondSubtitle"}) {
            JsonNode runs = at(header, key, "runs");
            if (runs.isArray()) {
                for (JsonNode r : runs) {
                    String t = textOf(r);
                    if (t != null) {
                        t = t.trim();
                        if (t.length() == 4 && t.chars().allMatch(Character::isDigit)) {
                            return t;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String bestAlbumThumbnail(JsonNode header) {
        for (String renderer : new String[]{"musicThumbnailRenderer", "croppedSquareThumbnailRenderer"}) {
            String url = bestThumbnailAt(header, "thumbnail", renderer, "thumbnail", "thumbnails");
            if (url != null) return url;
        }
        return null;
    }

    private YtTrackDto parseAlbumRow(JsonNode row, String albumTitle, String albumArtist, String albumThumbnail) {
        List<FlexColumn> cols = classifyFlexColumns(row);
        String videoId = null, title = "", rowArtist = null;
        Integer flexDuration = null;
        for (FlexColumn c : cols) {
            switch (c.kind()) {
                case TITLE -> {
                    if (title.isEmpty()) title = c.text();
                    if (videoId == null && c.videoId() != null) videoId = c.videoId();
                }
                case ARTIST -> { if (rowArtist == null) rowArtist = c.text(); }
                case DURATION -> { if (flexDuration == null) flexDuration = c.durationSecs(); }
                default -> {
                    // Other column kinds do not contribute to an album track.
                }
            }
        }
        if (videoId == null) {
            videoId = textAt(row, "playlistItemData", "videoId");
        }
        if (videoId == null || title.isEmpty()) {
            return null;
        }
        String primaryArtist = rowArtist != null ? rowArtist : albumArtist;
        if (primaryArtist == null) primaryArtist = "";
        int duration = fixedColumnsDuration(row);
        if (duration == 0 && flexDuration != null) duration = flexDuration;

        return YtTrackDto.builder()
                .videoId(videoId)
                .title(title)
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
        String name = runsText(header, "title", "runs");
        String description = runsText(header, "description", "runs");
        String banner = bestArtistBanner(header);

        List<YtTrackDto> topSongs = new ArrayList<>();
        List<YtAlbumDto> albums = new ArrayList<>();
        List<YtAlbumDto> singles = new ArrayList<>();
        for (JsonNode section : albumSectionContents(resp)) {
            JsonNode carousel = section.get("musicCarouselShelfRenderer");
            if (carousel != null) {
                // hl=en in the innertube context makes the carousel titles stable.
                String carouselTitle = safe(runsText(at(carousel, "header",
                        "musicCarouselShelfBasicHeaderRenderer"), "title", "runs")).toLowerCase();
                boolean isSingles = carouselTitle.contains("single") || carouselTitle.contains("sencillo");
                collectCarouselAlbums(carousel, isSingles ? singles : albums);
                continue;
            }
            JsonNode shelf = section.get("musicShelfRenderer");
            if (shelf != null && "Top songs".equalsIgnoreCase(safe(runsText(shelf, "title", "runs")))) {
                JsonNode items = shelf.get("contents");
                if (items != null && items.isArray()) {
                    for (JsonNode item : items) {
                        JsonNode row = item.get("musicResponsiveListItemRenderer");
                        if (row == null) continue;
                        YtTrackDto track = parseArtistSongRow(row);
                        if (track != null) topSongs.add(track);
                    }
                }
            }
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

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void collectCarouselAlbums(JsonNode carousel, List<YtAlbumDto> out) {
        JsonNode contents = carousel.get("contents");
        if (contents == null || !contents.isArray()) return;
        for (JsonNode tile : contents) {
            JsonNode r = tile.get("musicTwoRowItemRenderer");
            if (r == null) continue;
            String browseId = textAt(r, "navigationEndpoint", "browseEndpoint", "browseId");
            if (browseId == null || !browseId.startsWith("MPRE")) continue;
            String title = runsText(r, "title", "runs");
            String subtitle = runsText(r, "subtitle", "runs");
            String thumbnail = normalizeThumbnail(bestThumbnailAt(r,
                    "thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails"));
            out.add(YtAlbumDto.builder()
                    .browseId(browseId)
                    .title(title == null ? "" : title)
                    .artist(subtitle)
                    .thumbnailUrl(thumbnail)
                    .tracks(List.of())
                    .build());
        }
    }

    private JsonNode findArtistHeader(JsonNode resp) {
        JsonNode immersive = at(resp, "header", "musicImmersiveHeaderRenderer");
        if (!immersive.isMissingNode()) return immersive;
        JsonNode visual = at(resp, "header", "musicVisualHeaderRenderer");
        if (!visual.isMissingNode()) return visual;
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private String bestArtistBanner(JsonNode header) {
        for (String renderer : new String[]{"thumbnail", "foregroundThumbnail"}) {
            String url = bestThumbnailAt(header, renderer, "musicThumbnailRenderer", "thumbnail", "thumbnails");
            if (url != null) return normalizeThumbnail(url);
        }
        return null;
    }

    private YtTrackDto parseArtistSongRow(JsonNode row) {
        List<FlexColumn> cols = classifyFlexColumns(row);
        String videoId = null, title = "", artist = "", album = "";
        Integer flexDuration = null;
        for (FlexColumn c : cols) {
            switch (c.kind()) {
                case TITLE -> {
                    if (title.isEmpty()) title = c.text();
                    if (videoId == null && c.videoId() != null) videoId = c.videoId();
                }
                case ARTIST -> { if (artist.isEmpty()) artist = c.text(); }
                case ALBUM -> { if (album.isEmpty()) album = c.text(); }
                case DURATION -> { if (flexDuration == null) flexDuration = c.durationSecs(); }
                default -> {
                    // Other column kinds do not contribute to an artist track.
                }
            }
        }
        if (videoId == null) {
            videoId = textAt(row, "playlistItemData", "videoId");
        }
        if (videoId == null || title.isEmpty()) {
            return null;
        }
        int duration = fixedColumnsDuration(row);
        if (duration == 0 && flexDuration != null) duration = flexDuration;
        String thumbnail = normalizeThumbnail(bestThumbnailAt(row,
                "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"));

        return YtTrackDto.builder()
                .videoId(videoId)
                .title(title)
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
            JsonNode runs = at(col, "musicResponsiveListItemFlexColumnRenderer", "text", "runs");
            if (!runs.isArray() || runs.isEmpty()) {
                out.add(new FlexColumn(FlexColumn.Kind.EMPTY, "", null, null, 0));
                continue;
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode r : runs) {
                JsonNode text = r.get("text");
                if (text != null && text.isTextual()) sb.append(text.asText());
            }
            String text = sb.toString();
            if (text.isBlank()) {
                out.add(new FlexColumn(FlexColumn.Kind.EMPTY, "", null, null, 0));
                continue;
            }

            // Title check first, against runs[0] specifically — that's where
            // the watchEndpoint lives. Scanning every run's navigationEndpoint
            // can hand a multi-run "Title feat. Artist" column to the Artist
            // branch via run[1]'s browseEndpoint, dropping the row.
            JsonNode firstNav = runs.get(0).get("navigationEndpoint");
            String vid = firstNav == null ? null : textAt(firstNav, "watchEndpoint", "videoId");
            if (vid != null) {
                String pid = textAt(firstNav, "watchEndpoint", "playlistId");
                out.add(new FlexColumn(FlexColumn.Kind.TITLE, text, vid, pid, 0));
                continue;
            }

            FlexColumn classified = classifyByEndpoint(runs, text);
            if (classified != null) {
                out.add(classified);
                continue;
            }
            int secs = parseMmSs(text.trim());
            if (secs > 0 || text.trim().matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
                out.add(new FlexColumn(FlexColumn.Kind.DURATION, text, null, null, secs));
            } else if (isPlayCountText(text)) {
                out.add(new FlexColumn(FlexColumn.Kind.PLAY_COUNT, text, null, null, 0));
            } else {
                out.add(new FlexColumn(FlexColumn.Kind.OTHER, text, null, null, 0));
            }
        }
        return out;
    }

    private FlexColumn classifyByEndpoint(JsonNode runs, String text) {
        for (JsonNode r : runs) {
            JsonNode nav = r.get("navigationEndpoint");
            if (nav == null) continue;
            String browseId = textAt(nav, "browseEndpoint", "browseId");
            if (browseId != null) {
                if (browseId.startsWith("UC")) return new FlexColumn(FlexColumn.Kind.ARTIST, text, null, null, 0);
                if (browseId.startsWith("MPRE")) return new FlexColumn(FlexColumn.Kind.ALBUM, text, null, null, 0);
            }
            String playlistId = textAt(nav, "watchPlaylistEndpoint", "playlistId");
            if (playlistId == null) playlistId = textAt(nav, "watchEndpoint", "playlistId");
            if (playlistId != null && playlistId.startsWith("OLAK5uy_")) {
                return new FlexColumn(FlexColumn.Kind.ALBUM, text, null, null, 0);
            }
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
