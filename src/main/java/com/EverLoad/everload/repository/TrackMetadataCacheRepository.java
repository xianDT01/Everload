package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.TrackMetadataCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrackMetadataCacheRepository extends JpaRepository<TrackMetadataCache, Long> {
    Optional<TrackMetadataCache> findByNasPathIdAndRelativePath(Long nasPathId, String relativePath);
    List<TrackMetadataCache> findByNasPathIdAndRelativePathIn(Long nasPathId, Collection<String> relativePaths);
}
