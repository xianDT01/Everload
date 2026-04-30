package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.TrackMetadataCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrackMetadataCacheRepository extends JpaRepository<TrackMetadataCache, Long> {
    Optional<TrackMetadataCache> findByNasPathIdAndRelativePath(Long nasPathId, String relativePath);
    List<TrackMetadataCache> findByNasPathIdAndRelativePathIn(Long nasPathId, Collection<String> relativePaths);

    @Modifying
    @Transactional
    @Query("UPDATE TrackMetadataCache c " +
           "SET c.relativePath = CONCAT(:newPrefix, SUBSTRING(c.relativePath, :cutLen + 1)) " +
           "WHERE c.nasPathId = :nasPathId " +
           "AND (c.relativePath = :exactOld OR c.relativePath LIKE :likePrefix)")
    int renamePathPrefix(@Param("nasPathId") Long nasPathId,
                         @Param("exactOld") String exactOld,
                         @Param("likePrefix") String likePrefix,
                         @Param("cutLen") int cutLen,
                         @Param("newPrefix") String newPrefix);
}
