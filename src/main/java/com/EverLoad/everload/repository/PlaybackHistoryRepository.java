package com.everload.everload.repository;

import com.everload.everload.model.PlaybackHistory;
import com.everload.everload.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PlaybackHistoryRepository extends JpaRepository<PlaybackHistory, Long> {
    List<PlaybackHistory> findByUserOrderByPlayedAtDesc(User user, Pageable pageable);

    long countByUser(User user);

    @Modifying
    @Transactional
    @Query("UPDATE PlaybackHistory h SET h.title = :title, h.artist = :artist, h.album = :album " +
           "WHERE h.nasPathId = :nasPathId AND h.trackPath = :trackPath")
    int updateMetadataByTrack(@Param("nasPathId") Long nasPathId,
                              @Param("trackPath") String trackPath,
                              @Param("title") String title,
                              @Param("artist") String artist,
                              @Param("album") String album);

    @Query("SELECT h.trackPath, h.title, h.artist, h.album, h.nasPathId, COUNT(h) as cnt " +
           "FROM PlaybackHistory h WHERE h.user = :user " +
           "GROUP BY h.trackPath, h.title, h.artist, h.album, h.nasPathId " +
           "ORDER BY cnt DESC")
    List<Object[]> findTopPlayedByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT h.artist, COUNT(h) as cnt " +
           "FROM PlaybackHistory h WHERE h.user = :user AND h.artist IS NOT NULL AND h.artist <> '' " +
           "GROUP BY h.artist " +
           "ORDER BY cnt DESC")
    List<Object[]> findTopArtistsByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT h.artist, COUNT(h), COUNT(DISTINCT h.user.id) " +
           "FROM PlaybackHistory h WHERE h.artist IS NOT NULL AND h.artist <> '' " +
           "GROUP BY h.artist HAVING COUNT(DISTINCT h.user.id) >= 2 ORDER BY COUNT(h) DESC")
    List<Object[]> findCommunityTopArtists(Pageable pageable);

    @Query("SELECT h.title, h.artist, h.album, COUNT(h), COUNT(DISTINCT h.user.id) " +
           "FROM PlaybackHistory h WHERE h.title IS NOT NULL AND h.title <> '' " +
           "GROUP BY h.title, h.artist, h.album " +
           "HAVING COUNT(DISTINCT h.user.id) >= 2 ORDER BY COUNT(h) DESC")
    List<Object[]> findCommunityTopTracks(Pageable pageable);

    @Query("SELECT h.trackPath, h.title, h.artist, h.album, h.nasPathId, MAX(h.playedAt) as lastPlayed " +
           "FROM PlaybackHistory h WHERE h.user = :user " +
           "GROUP BY h.trackPath, h.title, h.artist, h.album, h.nasPathId " +
           "ORDER BY lastPlayed DESC")
    List<Object[]> findRecentUniqueByUser(@Param("user") User user, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM PlaybackHistory h WHERE h.playedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Transactional
    @Query("UPDATE PlaybackHistory h " +
           "SET h.trackPath = CONCAT(:newPrefix, SUBSTRING(h.trackPath, :cutLen + 1)) " +
           "WHERE h.nasPathId = :nasPathId " +
           "AND (h.trackPath = :exactOld OR h.trackPath LIKE :likePrefix)")
    int renamePathPrefix(@Param("nasPathId") Long nasPathId,
                         @Param("exactOld") String exactOld,
                         @Param("likePrefix") String likePrefix,
                         @Param("cutLen") int cutLen,
                         @Param("newPrefix") String newPrefix);

    @Modifying
    @Transactional
    @Query("DELETE FROM PlaybackHistory h " +
           "WHERE h.nasPathId = :nasPathId " +
           "AND (h.trackPath = :exactPath OR h.trackPath LIKE :likePrefix)")
    int deleteByPathPrefix(@Param("nasPathId") Long nasPathId,
                           @Param("exactPath") String exactPath,
                           @Param("likePrefix") String likePrefix);
}
