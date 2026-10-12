package com.everload.everload.service;

import com.everload.everload.dto.*;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YtMusicServiceTest {

    private YtMusicSearchService search;
    private YtMusicDiscoverService discover;
    private YtMusicPlaylistService playlists;
    private YtMusicMixService mixes;
    private YtMusicStreamService streams;
    private YtMusicService service;

    @BeforeEach
    void setUp() {
        search = mock(YtMusicSearchService.class);
        discover = mock(YtMusicDiscoverService.class);
        playlists = mock(YtMusicPlaylistService.class);
        mixes = mock(YtMusicMixService.class);
        streams = mock(YtMusicStreamService.class);
        service = new YtMusicService(search, discover, playlists, mixes, streams);
    }

    @Test
    void delegatesSearchOperations() {
        List<YtTrackDto> tracks = List.of(mock(YtTrackDto.class));
        List<String> suggestions = List.of("song");
        when(search.searchTracks("query")).thenReturn(tracks);
        when(search.suggestions("query")).thenReturn(suggestions);
        when(search.resolveArtistChannelId("artist")).thenReturn("channel");

        assertSame(tracks, service.search("query"));
        assertSame(suggestions, service.suggestions("query"));
        assertEquals("channel", service.resolveArtistChannelId("artist"));
        verify(search).searchTracks("query");
        verify(search).suggestions("query");
        verify(search).resolveArtistChannelId("artist");
    }

    @Test
    void delegatesDiscoverAndCatalogOperations() {
        YtDiscoverHomeDto home = mock(YtDiscoverHomeDto.class);
        YtAlbumDto album = mock(YtAlbumDto.class);
        YtArtistDto artist = mock(YtArtistDto.class);
        when(discover.fetchHome()).thenReturn(home);
        when(discover.fetchContinuation("next")).thenReturn(home);
        when(discover.fetchNewReleases()).thenReturn(home);
        when(discover.fetchCharts()).thenReturn(home);
        when(discover.fetchMoods()).thenReturn(home);
        when(discover.fetchMoodCategory("mood", "params")).thenReturn(home);
        when(discover.fetchAlbum("album")).thenReturn(album);
        when(discover.fetchArtist("channel")).thenReturn(artist);

        assertSame(home, service.discoverHome());
        assertSame(home, service.discoverContinuation("next"));
        assertSame(home, service.discoverNewReleases());
        assertSame(home, service.discoverCharts());
        assertSame(home, service.discoverMoods());
        assertSame(home, service.discoverMoodCategory("mood", "params"));
        assertSame(album, service.getAlbum("album"));
        assertSame(artist, service.getArtist("channel"));
        verify(discover).fetchContinuation("next");
        verify(discover).fetchMoodCategory("mood", "params");
    }

    @Test
    void delegatesPlaylistsMixAndStreamOperations() throws Exception {
        YtPlaylistSummaryDto summary = mock(YtPlaylistSummaryDto.class);
        YtStreamInfoDto stream = mock(YtStreamInfoDto.class);
        List<YtTrackDto> tracks = List.of(mock(YtTrackDto.class));
        when(playlists.fetchPlaylistSummary("playlist")).thenReturn(summary);
        when(playlists.getPlaylistEntries("playlist")).thenReturn(tracks);
        when(mixes.startMix("seed")).thenReturn(tracks);
        when(streams.resolveStream("video")).thenReturn(stream);
        HttpServletResponse response = mock(HttpServletResponse.class);

        assertSame(summary, service.getPlaylistSummary("playlist"));
        assertSame(tracks, service.getPlaylistEntries("playlist"));
        assertSame(tracks, service.startMix("seed"));
        assertSame(stream, service.getStream("video"));
        service.streamAudioToResponse("video", "bytes=0-10", response);

        verify(playlists).fetchPlaylistSummary("playlist");
        verify(playlists).getPlaylistEntries("playlist");
        verify(mixes).startMix("seed");
        verify(streams).resolveStream("video");
        verify(streams).streamAudioToResponse("video", "bytes=0-10", response);
    }

    @Test
    void exposesConfiguredEnabledFlag() {
        assertEquals(false, service.isEnabled());
        ReflectionTestUtils.setField(service, "enabled", true);
        assertEquals(true, service.isEnabled());
    }
}
