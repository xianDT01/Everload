package com.everload.everload.controller;

import com.everload.everload.model.Playlist;
import com.everload.everload.model.PlaylistCollaborator;
import com.everload.everload.model.PlaylistTrack;
import com.everload.everload.model.User;
import com.everload.everload.model.UserStatus;
import com.everload.everload.repository.PlaylistCollaboratorRepository;
import com.everload.everload.repository.PlaylistRepository;
import com.everload.everload.repository.PlaylistTrackRepository;
import com.everload.everload.repository.UserRepository;
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
import java.util.HashMap;

@Tag(name = "Playlists", description = "Gestión de playlists de usuario")
@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private static final String DELETED_KEY = "deleted";
    private static final String ERROR_KEY = "error";

    private final PlaylistRepository playlistRepository;
    private final PlaylistTrackRepository playlistTrackRepository;
    private final PlaylistCollaboratorRepository playlistCollaboratorRepository;
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

    @Operation(summary = "Listar playlists públicas de todos los usuarios")
    @GetMapping("/public")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<List<Playlist>> listPublic() {
        return ResponseEntity.ok(playlistRepository.findByIsPublicTrueOrderByCreatedAtDesc());
    }

    @Operation(summary = "Listar playlists colaborativas compartidas con el usuario")
    @GetMapping("/shared")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<List<Playlist>> listShared(@AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(playlistRepository.findSharedWithUser(getUser(ud)));
    }

    @Operation(summary = "Cambiar visibilidad de la playlist (pública/privada)")
    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> setVisibility(@AuthenticationPrincipal UserDetails ud,
                                           @PathVariable Long id,
                                           @RequestBody VisibilityDto dto) {
        return playlistRepository.findByIdAndUser(id, getUser(ud))
                .map(pl -> { pl.setIsPublic(dto.getIsPublic()); return okResponse(playlistRepository.save(pl)); })
                .orElse(ResponseEntity.notFound().build());
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
    public ResponseEntity<Object> rename(@AuthenticationPrincipal UserDetails ud,
                                    @PathVariable Long id,
                                    @RequestBody CreatePlaylistDto dto) {
        return playlistRepository.findByIdAndUser(id, getUser(ud))
                .map(pl -> { pl.setName(dto.getName().trim()); return okResponse(playlistRepository.save(pl)); })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Eliminar playlist")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> delete(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return playlistRepository.findByIdAndUser(id, getUser(ud))
                .map(pl -> { playlistRepository.delete(pl); return okResponse(Map.of(DELETED_KEY, true)); })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Tracks ────────────────────────────────────────────────────────────────

    @Operation(summary = "Añadir pista a playlist")
    @PostMapping("/{id}/tracks")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> addTrack(@AuthenticationPrincipal UserDetails ud,
                                      @PathVariable Long id,
                                      @RequestBody PlaylistTrackDto dto) {
        return playlistRepository.findByIdAndEditableByUser(id, getUser(ud))
                .map(pl -> {
                    if (playlistTrackRepository.existsByPlaylistAndTrackPathAndNasPathId(pl, dto.getTrackPath(), dto.getNasPathId())) {
                        return okResponse(playlistTrackRepository
                                .findByPlaylistAndTrackPathAndNasPathId(pl, dto.getTrackPath(), dto.getNasPathId())
                                .orElseThrow());
                    }
                    int pos = playlistTrackRepository.countByPlaylist(pl);
                    PlaylistTrack pt = PlaylistTrack.builder()
                            .playlist(pl)
                            .trackPath(dto.getTrackPath())
                            .title(dto.getTitle())
                            .artist(dto.getArtist())
                            .album(dto.getAlbum())
                            .source("youtube".equals(dto.getSource()) || "ytmusic".equals(dto.getSource()) ? dto.getSource() : "nas")
                            .nasPathId(dto.getNasPathId())
                            .durationSeconds(dto.getDurationSeconds())
                            .position(pos)
                            .build();
                    return okResponse(playlistTrackRepository.save(pt));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Eliminar pista de playlist")
    @DeleteMapping("/{id}/tracks/{trackId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> removeTrack(@AuthenticationPrincipal UserDetails ud,
                                         @PathVariable Long id,
                                         @PathVariable Long trackId) {
        return playlistRepository.findByIdAndEditableByUser(id, getUser(ud))
                .map(pl -> {
                    playlistTrackRepository.findByIdAndPlaylist(trackId, pl).ifPresent(playlistTrackRepository::delete);
                    return okResponse(Map.of(DELETED_KEY, true));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Reordenar pistas de una playlist")
    @PutMapping("/{id}/tracks/order")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> reorderTracks(@AuthenticationPrincipal UserDetails ud,
                                           @PathVariable Long id,
                                           @RequestBody ReorderTracksDto dto) {
        return playlistRepository.findByIdAndEditableByUser(id, getUser(ud))
                .map(pl -> {
                    List<Long> requestedIds = dto.getTrackIds() == null ? List.of() : dto.getTrackIds();
                    Map<Long, PlaylistTrack> byId = new HashMap<>();
                    pl.getTracks().forEach(track -> byId.put(track.getId(), track));

                    List<PlaylistTrack> ordered = new java.util.ArrayList<>();
                    for (Long trackId : requestedIds) {
                        PlaylistTrack track = byId.remove(trackId);
                        if (track != null) ordered.add(track);
                    }
                    ordered.addAll(pl.getTracks().stream()
                            .filter(track -> byId.containsKey(track.getId()))
                            .toList());

                    for (int position = 0; position < ordered.size(); position++) {
                        ordered.get(position).setPosition(position);
                    }
                    playlistTrackRepository.saveAll(ordered);
                    return okResponse(Map.of("reordered", ordered.size()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Colaboradores ─────────────────────────────────────────────────────────

    @Operation(summary = "Listar colaboradores de la playlist")
    @GetMapping("/{id}/collaborators")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> listCollaborators(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        return playlistRepository.findByIdAndEditableByUser(id, getUser(ud))
                .map(pl -> okResponse(pl.getCollaboratorUsernames()))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Buscar usuarios por nombre para compartir una playlist")
    @GetMapping("/users/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<List<String>> searchUsers(@AuthenticationPrincipal UserDetails ud,
                                                     @RequestParam("q") String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(userRepository.findTop10ByUsernameContainingIgnoreCaseAndStatus(q, UserStatus.ACTIVE)
                .stream()
                .map(User::getUsername)
                .filter(u -> !u.equalsIgnoreCase(ud.getUsername()))
                .toList());
    }

    @Operation(summary = "Añadir colaborador a la playlist (solo el dueño)")
    @PostMapping("/{id}/collaborators")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> addCollaborator(@AuthenticationPrincipal UserDetails ud,
                                             @PathVariable Long id,
                                             @RequestBody CollaboratorDto dto) {
        Playlist pl = playlistRepository.findByIdAndUser(id, getUser(ud)).orElse(null);
        if (pl == null) return ResponseEntity.notFound().build();

        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        User collaborator = userRepository.findByUsername(username).orElse(null);
        if (collaborator == null) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Usuario no encontrado"));
        }
        if (collaborator.getId().equals(pl.getUser().getId())) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "El dueño ya tiene acceso a la playlist"));
        }
        if (playlistCollaboratorRepository.existsByPlaylistAndUser(pl, collaborator)) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "El usuario ya es colaborador"));
        }
        playlistCollaboratorRepository.save(PlaylistCollaborator.builder()
                .playlist(pl)
                .user(collaborator)
                .build());
        List<String> usernames = playlistCollaboratorRepository.findByPlaylist(pl).stream()
                .map(PlaylistCollaborator::getUsername)
                .toList();
        return ResponseEntity.ok(usernames);
    }

    @Operation(summary = "Quitar colaborador de la playlist (solo el dueño)")
    @DeleteMapping("/{id}/collaborators/{username}")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> removeCollaborator(@AuthenticationPrincipal UserDetails ud,
                                                @PathVariable Long id,
                                                @PathVariable String username) {
        return playlistRepository.findByIdAndUser(id, getUser(ud))
                .map(pl -> {
                    User collaborator = userRepository.findByUsername(username).orElse(null);
                    if (collaborator != null) {
                        playlistCollaboratorRepository.deleteByPlaylistAndUser(pl, collaborator);
                    }
                    return okResponse(Map.of(DELETED_KEY, true));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Abandonar una playlist colaborativa")
    @PostMapping("/{id}/leave")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> leave(@AuthenticationPrincipal UserDetails ud, @PathVariable Long id) {
        User user = getUser(ud);
        return playlistRepository.findById(id)
                .map(pl -> {
                    playlistCollaboratorRepository.deleteByPlaylistAndUser(pl, user);
                    return okResponse(Map.of("left", true));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    private static ResponseEntity<Object> okResponse(Object body) {
        return ResponseEntity.ok(body);
    }

    @Data static class CreatePlaylistDto { private String name; }
    @Data static class VisibilityDto { private Boolean isPublic; }
    @Data static class CollaboratorDto { private String username; }
    @Data static class ReorderTracksDto { private List<Long> trackIds; }

    @Data static class PlaylistTrackDto {
        private String source;
        private String trackPath;
        private String title;
        private String artist;
        private String album;
        private Long nasPathId;
        private Integer durationSeconds;
    }
}
