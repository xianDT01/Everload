package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.FavoriteTrack;
import com.EverLoad.everload.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteTrackRepository extends JpaRepository<FavoriteTrack, Long> {
    List<FavoriteTrack> findByUser(User user, Sort sort);
    Optional<FavoriteTrack> findByUserAndTrackPathAndNasPathId(User user, String trackPath, Long nasPathId);
    boolean existsByUserAndTrackPathAndNasPathId(User user, String trackPath, Long nasPathId);
}
