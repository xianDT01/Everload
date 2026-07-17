package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.FavoriteTrackRequest;
import com.EverLoad.everload.dto.PlaybackHistoryRequest;
import com.EverLoad.everload.model.FavoriteTrack;
import com.EverLoad.everload.model.PlaybackHistory;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.repository.FavoriteTrackRepository;
import com.EverLoad.everload.repository.PlaybackHistoryRepository;
import com.EverLoad.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserLibraryControllerTest {

    private FavoriteTrackRepository favorites;
    private PlaybackHistoryRepository history;
    private UserLibraryController controller;
    private User user;
    private UserDetails principal;

    @BeforeEach
    void setUp() {
        favorites = mock(FavoriteTrackRepository.class);
        history = mock(PlaybackHistoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        controller = new UserLibraryController(favorites, history, users);
        user = User.builder().username("xianDT").build();
        principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("xianDT");
        when(users.findByUsername("xianDT")).thenReturn(Optional.of(user));
    }

    @Test
    void toggleFavoriteBuildsServerOwnedEntity() {
        FavoriteTrackRequest request = new FavoriteTrackRequest(
                "mixes/track.mp3", "Track", "Artist", "Album", 7L);
        when(favorites.findByUserAndTrackPathAndNasPathId(user, request.trackPath(), request.nasPathId()))
                .thenReturn(Optional.empty());

        controller.toggleFavorite(principal, request);

        ArgumentCaptor<FavoriteTrack> captor = ArgumentCaptor.forClass(FavoriteTrack.class);
        verify(favorites).save(captor.capture());
        FavoriteTrack saved = captor.getValue();
        assertSame(user, saved.getUser());
        assertEquals(request.trackPath(), saved.getTrackPath());
        assertEquals(request.title(), saved.getTitle());
        assertNull(saved.getId());
        assertNull(saved.getCreatedAt());
    }

    @Test
    void addHistoryBuildsServerOwnedEntity() {
        PlaybackHistoryRequest request = new PlaybackHistoryRequest(
                "sets/live.flac", "Live", "Artist", "Album", 9L, 180, true);

        controller.addHistory(principal, request);

        ArgumentCaptor<PlaybackHistory> captor = ArgumentCaptor.forClass(PlaybackHistory.class);
        verify(history).save(captor.capture());
        PlaybackHistory saved = captor.getValue();
        assertSame(user, saved.getUser());
        assertEquals(request.trackPath(), saved.getTrackPath());
        assertEquals(request.durationSeconds(), saved.getDurationSeconds());
        assertEquals(request.completed(), saved.getCompleted());
        assertNull(saved.getId());
        assertNull(saved.getPlayedAt());
    }

    @Test
    void statsMapTopTracksAndTotalPlayCount() {
        when(history.countByUser(user)).thenReturn(7L);
        when(history.findTopPlayedByUser(eq(user), any())).thenReturn(List.<Object[]>of(
                new Object[]{"track.mp3", "Track", "Artist", "Album", 4L, 3L}
        ));

        Map<?, ?> body = (Map<?, ?>) controller.getStats(principal, 10).getBody();
        List<?> tracks = (List<?>) body.get("topTracks");
        Map<?, ?> track = (Map<?, ?>) tracks.get(0);

        assertEquals(7L, body.get("totalPlays"));
        assertEquals("track.mp3", track.get("trackPath"));
        assertEquals(3L, track.get("playCount"));
    }

    @Test
    void topArtistsMapRepositoryRows() {
        when(history.findTopArtistsByUser(eq(user), any())).thenReturn(List.<Object[]>of(
                new Object[]{"Artist", 5L}
        ));

        List<?> body = (List<?>) controller.getTopArtists(principal, 20).getBody();
        Map<?, ?> artist = (Map<?, ?>) body.get(0);

        assertEquals("Artist", artist.get("artist"));
        assertEquals(5L, artist.get("playCount"));
    }
}
