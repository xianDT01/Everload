package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.FavoriteTrack;
import com.EverLoad.everload.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteTrackRepository extends JpaRepository<FavoriteTrack, Long> {
    List<FavoriteTrack> findByUser(User user, Sort sort);
    Optional<FavoriteTrack> findByUserAndTrackPathAndNasPathId(User user, String trackPath, Long nasPathId);
    boolean existsByUserAndTrackPathAndNasPathId(User user, String trackPath, Long nasPathId);

    long countByUser(User user);

    @Modifying
    @Transactional
    @Query("UPDATE FavoriteTrack f " +
           "SET f.trackPath = CONCAT(:newPrefix, SUBSTRING(f.trackPath, :cutLen + 1)) " +
           "WHERE f.nasPathId = :nasPathId " +
           "AND (f.trackPath = :exactOld OR f.trackPath LIKE :likePrefix)")
    int renamePathPrefix(@Param("nasPathId") Long nasPathId,
                         @Param("exactOld") String exactOld,
                         @Param("likePrefix") String likePrefix,
                         @Param("cutLen") int cutLen,
                         @Param("newPrefix") String newPrefix);
}
