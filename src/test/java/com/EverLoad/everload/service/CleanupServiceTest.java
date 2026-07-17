package com.EverLoad.everload.service;

import com.EverLoad.everload.model.FavoriteTrack;
import com.EverLoad.everload.model.NasPath;
import com.EverLoad.everload.model.PlaybackHistory;
import com.EverLoad.everload.model.TrackMetadataCache;
import com.EverLoad.everload.repository.AuditLogRepository;
import com.EverLoad.everload.repository.FavoriteTrackRepository;
import com.EverLoad.everload.repository.NasPathRepository;
import com.EverLoad.everload.repository.PlaybackHistoryRepository;
import com.EverLoad.everload.repository.RevokedTokenRepository;
import com.EverLoad.everload.repository.TrackMetadataCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CleanupServiceTest {

    @TempDir
    Path tempDir;

    private TrackMetadataCacheRepository metadataCacheRepository;
    private NasPathRepository nasPathRepository;
    private RevokedTokenRepository revokedTokenRepository;
    private PlaybackHistoryRepository playbackHistoryRepository;
    private FavoriteTrackRepository favoriteTrackRepository;
    private AuditLogRepository auditLogRepository;
    private CleanupService service;

    @BeforeEach
    void setUp() {
        metadataCacheRepository = mock(TrackMetadataCacheRepository.class);
        nasPathRepository = mock(NasPathRepository.class);
        revokedTokenRepository = mock(RevokedTokenRepository.class);
        playbackHistoryRepository = mock(PlaybackHistoryRepository.class);
        favoriteTrackRepository = mock(FavoriteTrackRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        service = new CleanupService(
                metadataCacheRepository,
                nasPathRepository,
                revokedTokenRepository,
                playbackHistoryRepository,
                favoriteTrackRepository,
                auditLogRepository
        );
    }

    @Test
    void purgeMetadataRemovesMissingRootsAndFiles() throws IOException {
        Files.writeString(tempDir.resolve("present.mp3"), "audio");
        NasPath root = NasPath.builder().id(1L).path(tempDir.toString()).build();
        TrackMetadataCache missingRoot = metadata(1L, 99L, "unknown.mp3");
        TrackMetadataCache missingFile = metadata(2L, 1L, "missing.mp3");
        TrackMetadataCache presentFile = metadata(3L, 1L, "present.mp3");
        when(metadataCacheRepository.findAll()).thenReturn(List.of(missingRoot, missingFile, presentFile));
        when(nasPathRepository.findById(99L)).thenReturn(Optional.empty());
        when(nasPathRepository.findById(1L)).thenReturn(Optional.of(root));

        Map<String, Object> result = service.purgeOrphanedMetadataCache();

        assertEquals(2, result.get("removed"));
        assertEquals(3, result.get("total"));
        verify(metadataCacheRepository).delete(missingRoot);
        verify(metadataCacheRepository).delete(missingFile);
        verify(metadataCacheRepository, never()).delete(presentFile);
    }

    @Test
    void purgeReferencesCountsMissingRootsAndTracks() throws IOException {
        Files.writeString(tempDir.resolve("present.mp3"), "audio");
        NasPath root = NasPath.builder().id(1L).path(tempDir.toString()).build();
        PlaybackHistory noRoot = history(1L, 99L, "track.mp3");
        PlaybackHistory noFile = history(2L, 1L, "missing.mp3");
        PlaybackHistory present = history(3L, 1L, "present.mp3");
        PlaybackHistory incomplete = history(4L, null, "present.mp3");
        FavoriteTrack favoriteMissing = favorite(5L, 1L, "missing.mp3");
        FavoriteTrack favoritePresent = favorite(6L, 1L, "present.mp3");
        when(playbackHistoryRepository.findAll()).thenReturn(List.of(noRoot, noFile, present, incomplete));
        when(favoriteTrackRepository.findAll()).thenReturn(List.of(favoriteMissing, favoritePresent));
        when(nasPathRepository.findById(99L)).thenReturn(Optional.empty());
        when(nasPathRepository.findById(1L)).thenReturn(Optional.of(root));

        Map<String, Object> result = service.purgeOrphanedReferences();

        assertEquals(2, result.get("historyRemoved"));
        assertEquals(4, result.get("historyTotal"));
        assertEquals(1, result.get("favoritesRemoved"));
        assertEquals(2, result.get("favoritesTotal"));
        verify(playbackHistoryRepository).delete(noRoot);
        verify(playbackHistoryRepository).delete(noFile);
        verify(playbackHistoryRepository, never()).delete(present);
        verify(playbackHistoryRepository, never()).delete(incomplete);
        verify(favoriteTrackRepository).delete(favoriteMissing);
        verify(favoriteTrackRepository, never()).delete(favoritePresent);
    }

    @Test
    void timedCleanupMethodsReturnRepositoryCounts() {
        when(revokedTokenRepository.deleteExpiredTokens(any(Instant.class))).thenReturn(3);
        when(playbackHistoryRepository.deleteOlderThan(any(LocalDateTime.class))).thenReturn(4);
        when(auditLogRepository.deleteOlderThan(any(LocalDateTime.class))).thenReturn(5);

        Map<String, Object> tokens = service.purgeExpiredTokens();
        Map<String, Object> history = service.trimPlaybackHistory(30);
        Map<String, Object> audit = service.trimAuditLogs(90);

        assertEquals(3, tokens.get("removed"));
        assertEquals(4, history.get("removed"));
        assertEquals(30, history.get("daysKept"));
        assertEquals(5, audit.get("removed"));
        assertEquals(90, audit.get("daysKept"));
    }

    private TrackMetadataCache metadata(Long id, Long nasPathId, String path) {
        return TrackMetadataCache.builder().id(id).nasPathId(nasPathId).relativePath(path).build();
    }

    private PlaybackHistory history(Long id, Long nasPathId, String path) {
        return PlaybackHistory.builder().id(id).nasPathId(nasPathId).trackPath(path).build();
    }

    private FavoriteTrack favorite(Long id, Long nasPathId, String path) {
        return FavoriteTrack.builder().id(id).nasPathId(nasPathId).trackPath(path).build();
    }
}
