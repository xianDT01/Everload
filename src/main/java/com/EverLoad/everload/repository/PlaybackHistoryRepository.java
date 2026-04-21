package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.PlaybackHistory;
import com.EverLoad.everload.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaybackHistoryRepository extends JpaRepository<PlaybackHistory, Long> {
    List<PlaybackHistory> findByUserOrderByPlayedAtDesc(User user, Pageable pageable);

    long countByUser(User user);

    @Query("SELECT h.trackPath, h.title, h.artist, h.album, h.nasPathId, COUNT(h) as cnt " +
           "FROM PlaybackHistory h WHERE h.user = :user " +
           "GROUP BY h.trackPath, h.title, h.artist, h.album, h.nasPathId " +
           "ORDER BY cnt DESC")
    List<Object[]> findTopPlayedByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT h.trackPath, h.title, h.artist, h.album, h.nasPathId, MAX(h.playedAt) as lastPlayed " +
           "FROM PlaybackHistory h WHERE h.user = :user " +
           "GROUP BY h.trackPath, h.title, h.artist, h.album, h.nasPathId " +
           "ORDER BY lastPlayed DESC")
    List<Object[]> findRecentUniqueByUser(@Param("user") User user, Pageable pageable);
}
