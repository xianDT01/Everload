package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.PlaybackHistory;
import com.EverLoad.everload.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaybackHistoryRepository extends JpaRepository<PlaybackHistory, Long> {
    List<PlaybackHistory> findByUserOrderByPlayedAtDesc(User user, Pageable pageable);
}
