package com.everload.everload.service;

import com.everload.everload.dto.YtTrackDto;
import com.everload.everload.model.SpotifyResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlaylistImportServiceTest {
    private final YtMusicPlaylistService youtube = mock(YtMusicPlaylistService.class);
    private final SpotifyService spotify = mock(SpotifyService.class);
    private final PlaylistImportService service = new PlaylistImportService(youtube, spotify);

    @Test void rejectsUntrustedUrlsBeforeContactingProviders() {
        for (String url : List.of("https://localhost/playlist?list=PL1234567890", "https://youtube.com.attacker.test/?list=PL1234567890", "http://youtube.com/playlist?list=PL1234567890", "https://user@youtube.com/?list=PL1234567890", "https://open.spotify.com/playlist/../../test")) {
            assertThrows(IllegalArgumentException.class, () -> service.read(url));
        }
        verifyNoInteractions(youtube, spotify);
    }

    @Test void importsYoutubeInOrderAndCountsUnavailableTracks() {
        when(youtube.getPlaylistEntries("PL1234567890")).thenReturn(List.of(
                YtTrackDto.builder().videoId("abcdefghijk").title("First").artist("Artist").durationSeconds(123).build(),
                YtTrackDto.builder().videoId(null).title("Unavailable").build(),
                YtTrackDto.builder().videoId("lmnopqrstuv").title("Last").build()));
        var result = service.read("https://music.youtube.com/playlist?list=PL1234567890&si=test");
        assertEquals(List.of("First", "Last"), result.tracks().stream().map(t -> t.getTitle()).toList());
        assertEquals(1, result.skipped());
        assertEquals("youtube", result.tracks().get(0).getSource());
        assertEquals(0L, result.tracks().get(0).getNasPathId());
        assertEquals(123, result.tracks().get(0).getDurationSeconds());
    }

    @Test void spotifySkipsMissingMatchesAndRetainsArtist() {
        when(spotify.getPlaylistTracks("1234567890123456789012", 100)).thenReturn(List.of(
                new SpotifyResult("Song", "https://www.youtube.com/watch?v=abcdefghijk", "Singer"),
                new SpotifyResult("Missing", null)));
        var result = service.read("https://open.spotify.com/playlist/1234567890123456789012?si=test");
        assertEquals(1, result.skipped());
        assertEquals("Singer", result.tracks().get(0).getArtist());
        assertTrue(result.spotifySource());
    }

    @Test void emptyProviderResponseDoesNotCreateEmptyPlaylist() {
        when(youtube.getPlaylistEntries("PL1234567890")).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.read("https://youtube.com/playlist?list=PL1234567890"));
    }
}
