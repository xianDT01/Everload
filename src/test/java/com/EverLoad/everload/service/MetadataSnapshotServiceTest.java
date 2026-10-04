package com.everload.everload.service;

import com.everload.everload.repository.FavoriteTrackRepository;
import com.everload.everload.repository.PlaybackHistoryRepository;
import com.everload.everload.repository.PlaylistTrackRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MetadataSnapshotServiceTest {

    @Test
    void refreshesPlaylistFavoritesAndHistoryWithNormalizedMetadata() {
        PlaylistTrackRepository playlists = mock(PlaylistTrackRepository.class);
        FavoriteTrackRepository favorites = mock(FavoriteTrackRepository.class);
        PlaybackHistoryRepository history = mock(PlaybackHistoryRepository.class);
        MetadataSnapshotService service = new MetadataSnapshotService(playlists, favorites, history);

        service.refreshTrack(7L, "Music/Tacata.mp3", " Tacata ", " Tacabro ", " ");

        verify(playlists).updateMetadataByTrack(7L, "Music/Tacata.mp3", "Tacata", "Tacabro", "");
        verify(favorites).updateMetadataByTrack(7L, "Music/Tacata.mp3", "Tacata", "Tacabro", "");
        verify(history).updateMetadataByTrack(7L, "Music/Tacata.mp3", "Tacata", "Tacabro", "");
    }

    @Test
    void fallsBackToFilenameWhenTitleIsCleared() {
        PlaylistTrackRepository playlists = mock(PlaylistTrackRepository.class);
        FavoriteTrackRepository favorites = mock(FavoriteTrackRepository.class);
        PlaybackHistoryRepository history = mock(PlaybackHistoryRepository.class);
        MetadataSnapshotService service = new MetadataSnapshotService(playlists, favorites, history);

        service.refreshTrack(7L, "Music/Tacata.mp3", "", null, null);

        verify(playlists).updateMetadataByTrack(7L, "Music/Tacata.mp3", "Tacata", "", "");
    }
}
