package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.Playlist;
import com.EverLoad.everload.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    List<Playlist> findByUserOrderByCreatedAtDesc(User user);
    Optional<Playlist> findByIdAndUser(Long id, User user);
}
