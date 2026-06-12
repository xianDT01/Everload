package com.EverLoad.everload.repository;

import com.EverLoad.everload.model.Playlist;
import com.EverLoad.everload.model.PlaylistTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrack, Long> {
    int countByPlaylist(Playlist playlist);
    boolean existsByPlaylistAndTrackPathAndNasPathId(Playlist playlist, String trackPath, Long nasPathId);
    java.util.Optional<PlaylistTrack> findByPlaylistAndTrackPathAndNasPathId(Playlist playlist, String trackPath, Long nasPathId);
    java.util.Optional<PlaylistTrack> findByIdAndPlaylist(Long id, Playlist playlist);

    @Modifying
    @Transactional
    @Query("DELETE FROM PlaylistTrack pt WHERE pt.playlist = :playlist AND pt.trackPath = :trackPath AND pt.nasPathId = :nasPathId")
    int deleteByPlaylistAndTrackPathAndNasPathId(
            @Param("playlist") Playlist playlist,
            @Param("trackPath") String trackPath,
            @Param("nasPathId") Long nasPathId);
}
