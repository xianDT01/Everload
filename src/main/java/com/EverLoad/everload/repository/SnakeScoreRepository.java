package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.SnakeScore;
import com.EverLoad.everload.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnakeScoreRepository extends JpaRepository<SnakeScore, Long> {

    /** Best score per user, ordered by score descending. Returns [username, maxScore, avatarFilename]. */
    @Query("SELECT s.user.username, MAX(s.score), s.user.avatarFilename " +
           "FROM SnakeScore s " +
           "GROUP BY s.user.id, s.user.username, s.user.avatarFilename " +
           "ORDER BY MAX(s.score) DESC")
    List<Object[]> findLeaderboard(Pageable pageable);

    Optional<SnakeScore> findTopByUserOrderByScoreDesc(User user);
}
