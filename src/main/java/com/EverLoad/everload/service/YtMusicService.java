package com.EverLoad.everload.service;

import com.EverLoad.everload.dto.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Facade over the whole YouTube Music module — the only YT-Music type
 * {@code YtMusicController} depends on. Internally it fans out to the
 * focused services (search, discover, playlists, mixes, stream resolution),
 * but callers only ever see this one seam: adding/reshaping an internal
 * service never ripples into the controller.
 *
 * <p>Anonymous mode only: every method here reaches only the public
 * catalogue surfaces (search, browse, public playlists, radio queues,
 * stream resolution). There is no library, no playlist mutation, no
 * follows/likes — by construction, not by omission.
 */
@Service
public class YtMusicService {

    private final YtMusicSearchService searchService;
    private final YtMusicDiscoverService discoverService;
    private final YtMusicPlaylistService playlistService;
    private final YtMusicMixService mixService;
    private final YtMusicStreamService streamService;

    @Value("${ytmusic.enabled:true}")
    private boolean enabled;

    public YtMusicService(YtMusicSearchService searchService,
                          YtMusicDiscoverService discoverService,
                          YtMusicPlaylistService playlistService,
                          YtMusicMixService mixService,
                          YtMusicStreamService streamService) {
        this.searchService = searchService;
        this.discoverService = discoverService;
        this.playlistService = playlistService;
        this.mixService = mixService;
        this.streamService = streamService;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ── Search ────────────────────────────────────────────────────────

    public List<YtTrackDto> search(String query) {
        return searchService.searchTracks(query);
    }

    public List<String> suggestions(String query) {
        return searchService.suggestions(query);
    }

    public String resolveArtistChannelId(String artistName) {
        return searchService.resolveArtistChannelId(artistName);
    }

    // ── Discover ──────────────────────────────────────────────────────

    public YtDiscoverHomeDto discoverHome() {
        return discoverService.fetchHome();
    }

    public YtDiscoverHomeDto discoverContinuation(String token) {
        return discoverService.fetchContinuation(token);
    }

    public YtDiscoverHomeDto discoverNewReleases() {
        return discoverService.fetchNewReleases();
    }

    public YtDiscoverHomeDto discoverCharts() {
        return discoverService.fetchCharts();
    }

    public YtDiscoverHomeDto discoverMoods() {
        return discoverService.fetchMoods();
    }

    public YtDiscoverHomeDto discoverMoodCategory(String browseId, String params) {
        return discoverService.fetchMoodCategory(browseId, params);
    }

    public YtAlbumDto getAlbum(String browseId) {
        return discoverService.fetchAlbum(browseId);
    }

    public YtArtistDto getArtist(String channelId) {
        return discoverService.fetchArtist(channelId);
    }

    // ── Playlists ─────────────────────────────────────────────────────

    public YtPlaylistSummaryDto getPlaylistSummary(String playlistId) {
        return playlistService.fetchPlaylistSummary(playlistId);
    }

    public List<YtTrackDto> getPlaylistEntries(String playlistId) {
        return playlistService.getPlaylistEntries(playlistId);
    }

    // ── Mixes / radio ─────────────────────────────────────────────────

    public List<YtTrackDto> startMix(String seedVideoId) {
        return mixService.startMix(seedVideoId);
    }

    // ── Stream resolution ─────────────────────────────────────────────

    /**
     * @throws YtStreamUnavailableException when every registered resolver
     *         failed — carries the per-resolver failure trail (restricted,
     *         geo-blocked, deleted, age-gated, Botguard error, yt-dlp
     *         exhausted...) for the controller to report explicitly.
     */
    public YtStreamInfoDto getStream(String videoId) {
        return streamService.resolveStream(videoId);
    }

    public void streamAudioToResponse(String videoId, String rangeHeader, HttpServletResponse response)
            throws IOException, InterruptedException {
        streamService.streamAudioToResponse(videoId, rangeHeader, response);
    }
}
