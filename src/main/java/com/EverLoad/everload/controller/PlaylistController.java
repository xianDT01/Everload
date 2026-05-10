package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.Playlist;
import com.EverLoad.everload.model.PlaylistTrack;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.repository.PlaylistRepository;
import com.EverLoad.everload.repository.PlaylistTrackRepository;
import com.EverLoad.everload.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Playlists", description = "Gestión de playlists de usuario")
@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final UserRepository userRepository;

    private User getUser(UserDetails ud) {
        return userRepository.findByUsername(ud.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    // ── Playlist CRUD ─────────────────────────────────────────────────────────

    @Operation(summary = "Listar playlists del usuario")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<List<Playlist>> list(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(playlistRepository.findByUserOrderByCreatedAtDesc(getUser(ud)));
    }

    @Operation(summary = "Crear playlist")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Playlist> create(@AuthenticationPrincipal UserDetails ud,
                                           @RequestBody CreatePlaylistDto dto) {
        Playlist pl = Playlist.builder()
                .user(getUser(ud))
                .name(dto.getName().trim())
                .build();
        return ResponseEntity.ok(playlistRepository.save(pl));
    }

    @Operation(summary = "Renombrar playlist")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> rename(@AuthenticationPrincipal UserDetails ud,
                                    @PathVariable Long id,
                                    @RequestBody CreatePlaylistDto dto) {
        return playlistRepository.findByIdAndUser(id, getUser(ud))
                .map(pl -> { pl.setName(dto.getName().trim()); return ResponseEntity.ok(playlistRepository.save(pl)); })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Eliminar playlist")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> delete(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return playlistRepository.findByIdAndUser(id, getUser(ud))
                .map(pl -> { playlistRepository.delete(pl); return ResponseEntity.ok(Map.of("deleted", true)); })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Tracks ────────────────────────────────────────────────────────────────

    @Operation(summary = "Añadir pista a playlist")
    @PostMapping("/{id}/tracks")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> addTrack(@AuthenticationPrincipal UserDetails ud,
                                      @PathVariable Long id,
                                      @RequestBody PlaylistTrackDto dto) {
        return playlistRepository.findByIdAndUser(id, getUser(ud))
                .map(pl -> {
                    int pos = playlistTrackRepository.countByPlaylist(pl);
                    PlaylistTrack pt = PlaylistTrack.builder()
                            .playlist(pl)
                            .trackPath(dto.getTrackPath())
                            .title(dto.getTitle())
                            .artist(dto.getArtist())
                            .album(dto.getAlbum())
                            .nasPathId(dto.getNasPathId())
                            .durationSeconds(dto.getDurationSeconds())
                            .position(pos)
                            .build();
                    return ResponseEntity.ok(playlistTrackRepository.save(pt));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Eliminar pista de playlist")
    @DeleteMapping("/{id}/tracks/{trackId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> removeTrack(@AuthenticationPrincipal UserDetails ud,
                                         @PathVariable Long id,
                                         @PathVariable Long trackId) {
        return playlistRepository.findByIdAndUser(id, getUser(ud))
                .map(pl -> {
                    playlistTrackRepository.findById(trackId).ifPresent(playlistTrackRepository::delete);
                    return ResponseEntity.ok(Map.of("deleted", true));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Data static class CreatePlaylistDto { private String name; }

    @Data static class PlaylistTrackDto {
        private String trackPath, title, artist, album;
        private Long nasPathId;
        private Integer durationSeconds;
    }
}
