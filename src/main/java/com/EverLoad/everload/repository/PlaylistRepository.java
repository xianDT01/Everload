package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.Playlist;
import com.EverLoad.everload.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    @EntityGraph(attributePaths = {"user", "tracks", "collaborators", "collaborators.user"})
    List<Playlist> findByUserOrderByCreatedAtDesc(User user);

    @EntityGraph(attributePaths = {"user", "tracks", "collaborators", "collaborators.user"})
    Optional<Playlist> findByIdAndUser(Long id, User user);

    @EntityGraph(attributePaths = {"user", "tracks", "collaborators", "collaborators.user"})
    List<Playlist> findByIsPublicTrueOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "tracks", "collaborators", "collaborators.user"})
    @Query("SELECT DISTINCT p FROM Playlist p JOIN p.collaborators c WHERE c.user = :user ORDER BY p.createdAt DESC")
    List<Playlist> findSharedWithUser(@Param("user") User user);

    @EntityGraph(attributePaths = {"user", "tracks", "collaborators", "collaborators.user"})
    @Query("SELECT DISTINCT p FROM Playlist p LEFT JOIN p.collaborators c WHERE p.id = :id AND (p.user = :user OR c.user = :user)")
    Optional<Playlist> findByIdAndEditableByUser(@Param("id") Long id, @Param("user") User user);
}
