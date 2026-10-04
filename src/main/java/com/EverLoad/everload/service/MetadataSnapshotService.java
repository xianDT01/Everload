package com.everload.everload.service;

import com.everload.everload.repository.FavoriteTrackRepository;
import com.everload.everload.repository.PlaybackHistoryRepository;
import com.everload.everload.repository.PlaylistTrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetadataSnapshotService {

    private final PlaylistTrackRepository playlistTrackRepository;
    private final FavoriteTrackRepository favoriteTrackRepository;
    private final PlaybackHistoryRepository playbackHistoryRepository;

    public void refreshTrack(Long nasPathId, String trackPath, String title, String artist, String album) {
        String currentTitle = title == null || title.isBlank() ? filenameWithoutExtension(trackPath) : title.trim();
        String currentArtist = artist == null ? "" : artist.trim();
        String currentAlbum = album == null ? "" : album.trim();

        playlistTrackRepository.updateMetadataByTrack(nasPathId, trackPath, currentTitle, currentArtist, currentAlbum);
        favoriteTrackRepository.updateMetadataByTrack(nasPathId, trackPath, currentTitle, currentArtist, currentAlbum);
        playbackHistoryRepository.updateMetadataByTrack(nasPathId, trackPath, currentTitle, currentArtist, currentAlbum);
    }

    private String filenameWithoutExtension(String path) {
        if (path == null || path.isBlank()) return "";
        String filename = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
        int extension = filename.lastIndexOf('.');
        return extension > 0 ? filename.substring(0, extension) : filename;
    }
}
