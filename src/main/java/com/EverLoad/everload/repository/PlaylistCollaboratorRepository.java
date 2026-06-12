package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.Playlist;
import com.EverLoad.everload.model.PlaylistCollaborator;
import com.EverLoad.everload.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PlaylistCollaboratorRepository extends JpaRepository<PlaylistCollaborator, Long> {

    @EntityGraph(attributePaths = {"user"})
    List<PlaylistCollaborator> findByPlaylist(Playlist playlist);

    Optional<PlaylistCollaborator> findByPlaylistAndUser(Playlist playlist, User user);

    boolean existsByPlaylistAndUser(Playlist playlist, User user);

    @Modifying
    @Transactional
    void deleteByPlaylistAndUser(Playlist playlist, User user);
}
