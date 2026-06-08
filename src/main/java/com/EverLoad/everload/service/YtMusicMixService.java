package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.YtTrackDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.EverLoad.everload.service.YtMusicJsonUtils.*;

/**
 * Auto-generated radio queues ("mixes"): given a seed track, ask YouTube
 * Music for the {@code RDAMVM<videoId>} auto-mix playlist via {@code /next}.
 * This is the same "Start radio" queue any anonymous visitor gets from a
 * watch page — no library or personalization state involved.
 */
@Service
public class YtMusicMixService {

    private final YtMusicInnertubeClient client;

    public YtMusicMixService(YtMusicInnertubeClient client) {
        this.client = client;
    }

    public List<YtTrackDto> startMix(String seedVideoId) {
        String playlistId = "RDAMVM" + seedVideoId;
        Map<String, Object> body = new LinkedHashMap<>(client.baseBodyFor(YtMusicClient.MAIN_CLIENT));
        body.put("videoId", seedVideoId);
        body.put("playlistId", playlistId);
        body.put("isAudioOnly", true);
        return walkQueue(client.next(body));
    }

    private List<YtTrackDto> walkQueue(JsonNode resp) {
        JsonNode items = at(resp, "contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer",
                "watchNextTabbedResultsRenderer", "tabs", "0", "tabRenderer", "content",
                "musicQueueRenderer", "content", "playlistPanelRenderer", "contents");
        List<YtTrackDto> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                YtTrackDto track = parseQueueRow(item);
                if (track != null && seen.add(track.getVideoId())) {
                    out.add(track);
                }
            }
        }
        return out;
    }

    private YtTrackDto parseQueueRow(JsonNode item) {
        JsonNode row = item.get("playlistPanelVideoRenderer");
        if (row == null) {
            return null;
        }
        String videoId = textAt(row, "videoId");
        if (videoId == null) {
            videoId = textAt(row, "navigationEndpoint", "watchEndpoint", "videoId");
        }
        if (videoId == null) {
            return null;
        }
        String title = runsText(row, "title", "runs");
        String thumbnail = normalizeThumbnail(bestThumbnailAt(row, "thumbnail", "thumbnails"));

        // longBylineText runs read "Artist • Album • mm:ss" with separators
        // stripped; the trailing token is the duration whenever it parses as
        // one (mood/video-mix rows sometimes omit the album segment).
        List<String> tokens = new ArrayList<>();
        JsonNode runs = at(row, "longBylineText", "runs");
        if (runs.isArray()) {
            for (JsonNode r : runs) {
                JsonNode text = r.get("text");
                if (text != null && text.isTextual() && !isSeparator(text.asText())) {
                    tokens.add(text.asText());
                }
            }
        }
        int duration = 0;
        if (!tokens.isEmpty()) {
            String last = tokens.get(tokens.size() - 1).trim();
            if (last.matches("\\d{1,2}:\\d{2}(:\\d{2})?")) {
                duration = parseMmSs(last);
                tokens.remove(tokens.size() - 1);
            }
        }
        String artist = tokens.isEmpty() ? "" : tokens.get(0);
        String album = tokens.size() > 1 ? tokens.get(1) : null;

        return YtTrackDto.builder()
                .videoId(videoId)
                .title(title == null ? "" : title)
                .artist(artist)
                .artists(artist.isBlank() ? List.of() : List.of(artist))
                .album(album == null ? "" : album)
                .albumId(synthesizeAlbumId(album, artist))
                .durationSeconds(duration)
                .thumbnailUrl(thumbnail)
                .build();
    }
}
