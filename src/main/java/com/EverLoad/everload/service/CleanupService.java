package com.EverLoad.everload.service;

import com.EverLoad.everload.model.NasPath;
import com.EverLoad.everload.model.TrackMetadataCache;
import com.EverLoad.everload.repository.AuditLogRepository;
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
