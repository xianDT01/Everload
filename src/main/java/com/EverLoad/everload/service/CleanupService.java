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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CleanupService {

    private final TrackMetadataCacheRepository metadataCacheRepo;
    private final NasPathRepository nasPathRepository;
    private final RevokedTokenRepository revokedTokenRepo;
    private final PlaybackHistoryRepository playbackHistoryRepo;
    private final FavoriteTrackRepository favoriteTrackRepo;
    private final AuditLogRepository auditLogRepo;

    @Transactional
    public Map<String, Object> purgeOrphanedMetadataCache() {
        List<TrackMetadataCache> all = metadataCacheRepo.findAll();
        int total = all.size();
        int removed = 0;
        for (TrackMetadataCache entry : all) {
            NasPath nasPath = nasPathRepository.findById(entry.getNasPathId()).orElse(null);
            if (nasPath == null) {
                metadataCacheRepo.delete(entry);
                removed++;
                continue;
            }
            File file = Path.of(nasPath.getPath()).resolve(entry.getRelativePath()).toFile();
            if (!file.exists()) {
                metadataCacheRepo.delete(entry);
                removed++;
            }
        }
        return Map.of("removed", removed, "total", total);
    }

    @Transactional
    public Map<String, Object> purgeExpiredTokens() {
        int removed = revokedTokenRepo.deleteExpiredTokens(Instant.now());
        return Map.of("removed", removed);
    }

    @Transactional
    public Map<String, Object> purgeOrphanedReferences() {
        int historyRemoved = 0;
        int historyTotal = 0;
        for (PlaybackHistory entry : playbackHistoryRepo.findAll()) {
            historyTotal++;
            if (isOrphaned(entry.getNasPathId(), entry.getTrackPath())) {
                playbackHistoryRepo.delete(entry);
                historyRemoved++;
            }
        }

        int favoritesRemoved = 0;
        int favoritesTotal = 0;
        for (FavoriteTrack entry : favoriteTrackRepo.findAll()) {
            favoritesTotal++;
            if (isOrphaned(entry.getNasPathId(), entry.getTrackPath())) {
                favoriteTrackRepo.delete(entry);
                favoritesRemoved++;
            }
        }

        return Map.of(
                "historyRemoved", historyRemoved,
                "historyTotal", historyTotal,
                "favoritesRemoved", favoritesRemoved,
                "favoritesTotal", favoritesTotal
        );
    }

    private boolean isOrphaned(Long nasPathId, String trackPath) {
        if (nasPathId == null || trackPath == null) return false;
        NasPath nasPath = nasPathRepository.findById(nasPathId).orElse(null);
        if (nasPath == null) return true;
        File file = Path.of(nasPath.getPath()).resolve(trackPath).toFile();
        return !file.exists();
    }

    @Transactional
    public Map<String, Object> trimPlaybackHistory(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        int removed = playbackHistoryRepo.deleteOlderThan(cutoff);
        return Map.of("removed", removed, "daysKept", daysToKeep);
    }

    @Transactional
    public Map<String, Object> trimAuditLogs(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        int removed = auditLogRepo.deleteOlderThan(cutoff);
        return Map.of("removed", removed, "daysKept", daysToKeep);
    }
}
