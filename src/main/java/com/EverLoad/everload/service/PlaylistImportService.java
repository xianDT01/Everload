package com.everload.everload.service;

import com.everload.everload.model.PlaylistTrack;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaylistImportService {
    private final YtMusicPlaylistService youtube;
    private final SpotifyService spotify;

    public record Imported(List<PlaylistTrack> tracks, int skipped, boolean spotifySource) {}

    public Imported read(String url) {
        URI uri = parse(url);
        List<PlaylistTrack> tracks = new ArrayList<>();
        int skipped = 0;
        boolean fromSpotify = "open.spotify.com".equalsIgnoreCase(uri.getHost());
        if (fromSpotify) {
            String path = uri.getPath();
            if (path == null || !path.matches("/(intl-[a-z]{2}/)?playlist/[A-Za-z0-9]{22}/?")) {
                throw new IllegalArgumentException("Invalid Spotify playlist URL");
            }
            String id = path.replaceAll("/$", "");
            id = id.substring(id.lastIndexOf('/') + 1);
            for (var entry : spotify.getPlaylistTracks(id, 100)) {
                String video = entry.getYoutubeUrl() == null ? "" : parameter(parse(entry.getYoutubeUrl()), "v");
                if (!video.matches("[A-Za-z0-9_-]{11}")) { skipped++; continue; }
                tracks.add(track(video, entry.getTitle(), entry.getArtist(), "", 0));
            }
        } else {
            if (!List.of("youtube.com", "www.youtube.com", "music.youtube.com", "m.youtube.com")
                    .contains(uri.getHost().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Unsupported playlist provider");
            }
            String id = parameter(uri, "list");
            if (!id.matches("[A-Za-z0-9_-]{10,100}")) throw new IllegalArgumentException("Invalid YouTube playlist URL");
            var entries = youtube.getPlaylistEntries(id);
            for (var entry : entries) {
                if (tracks.size() >= 1000 || entry.getVideoId() == null || !entry.getVideoId().matches("[A-Za-z0-9_-]{11}")) {
                    skipped++;
                    continue;
                }
                tracks.add(track(entry.getVideoId(), entry.getTitle(), entry.getArtist(), entry.getAlbum(), entry.getDurationSeconds()));
            }
        }
        if (tracks.isEmpty()) throw new IllegalArgumentException("No playable tracks found");
        return new Imported(tracks, skipped, fromSpotify);
    }

    private PlaylistTrack track(String id, String title, String artist, String album, Integer duration) {
        return PlaylistTrack.builder().trackPath(id).source("youtube").nasPathId(0L)
                .title(trim(title == null || title.isBlank() ? id : title)).artist(trim(artist)).album(trim(album))
                .durationSeconds(duration).build();
    }

    private String trim(String value) { return value == null ? "" : value.substring(0, Math.min(255, value.length())); }

    private URI parse(String url) {
        if (url == null || url.length() > 2048) throw new IllegalArgumentException("Invalid playlist URL");
        URI uri = URI.create(url.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new IllegalArgumentException("Invalid playlist URL");
        }
        return uri;
    }

    private String parameter(URI uri, String name) {
        if (uri.getRawQuery() == null) return "";
        for (String part : uri.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
        return "";
    }
}
