package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.FavoriteTrack;
import com.EverLoad.everload.model.PlaybackHistory;
import com.EverLoad.everload.model.User;
import com.EverLoad.everload.dto.FavoriteTrackRequest;
import com.EverLoad.everload.dto.PlaybackHistoryRequest;
import com.EverLoad.everload.repository.FavoriteTrackRepository;
import com.EverLoad.everload.repository.PlaybackHistoryRepository;
import com.EverLoad.everload.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "User Library", description = "Gestión de favoritos e historial del usuario")
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class UserLibraryController {

    private final FavoriteTrackRepository favoriteTrackRepository;
    private final PlaybackHistoryRepository playbackHistoryRepository;
    private final UserRepository userRepository;

    private User getAuthenticatedUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    // ── Favorites ────────────────────────────────────────────────────────────

    @Operation(summary = "Obtener pistas favoritas del usuario")
    @GetMapping("/favorites")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<List<FavoriteTrack>> getFavorites(@AuthenticationPrincipal UserDetails userDetails) {
        User user = getAuthenticatedUser(userDetails);
        List<FavoriteTrack> favorites = favoriteTrackRepository.findByUser(user, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(favorites);
    }

    @Operation(summary = "Añadir o quitar pista de favoritos (toggle)")
    @PostMapping("/favorites/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> toggleFavorite(@AuthenticationPrincipal UserDetails userDetails,
                                            @RequestBody FavoriteTrackRequest request) {
        User user = getAuthenticatedUser(userDetails);
        var existing = favoriteTrackRepository.findByUserAndTrackPathAndNasPathId(user, request.trackPath(), request.nasPathId());
        
        if (existing.isPresent()) {
            favoriteTrackRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of("message", "Removed from favorites", "isFavorite", false));
        } else {
            FavoriteTrack favorite = FavoriteTrack.builder()
                    .user(user)
                    .trackPath(request.trackPath())
                    .title(request.title())
                    .artist(request.artist())
                    .album(request.album())
                    .nasPathId(request.nasPathId())
                    .build();
            favoriteTrackRepository.save(favorite);
            return ResponseEntity.ok(Map.of("message", "Added to favorites", "isFavorite", true));
        }
    }

    @Operation(summary = "Comprobar si una pista es favorita")
    @GetMapping("/favorites/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> checkFavorite(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestParam String trackPath,
                                           @RequestParam Long nasPathId) {
        User user = getAuthenticatedUser(userDetails);
        boolean isFav = favoriteTrackRepository.existsByUserAndTrackPathAndNasPathId(user, trackPath, nasPathId);
        return ResponseEntity.ok(Map.of("isFavorite", isFav));
    }

    // ── Playback History ─────────────────────────────────────────────────────

    @Operation(summary = "Obtener historial de reproducción reciente")
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<List<PlaybackHistory>> getHistory(@AuthenticationPrincipal UserDetails userDetails,
                                                            @RequestParam(defaultValue = "50") int limit) {
        User user = getAuthenticatedUser(userDetails);
        List<PlaybackHistory> history = playbackHistoryRepository.findByUserOrderByPlayedAtDesc(user, PageRequest.of(0, limit));
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Registrar reproducción en el historial")
    @PostMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> addHistory(@AuthenticationPrincipal UserDetails userDetails,
                                        @RequestBody PlaybackHistoryRequest request) {
        User user = getAuthenticatedUser(userDetails);
        PlaybackHistory history = PlaybackHistory.builder()
                .user(user)
                .trackPath(request.trackPath())
                .title(request.title())
                .artist(request.artist())
                .album(request.album())
                .nasPathId(request.nasPathId())
                .durationSeconds(request.durationSeconds())
                .completed(request.completed())
                .build();
        playbackHistoryRepository.save(history);
        return ResponseEntity.ok(Map.of("message", "History recorded"));
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    @Operation(summary = "Estadísticas de escucha del usuario")
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> getStats(@AuthenticationPrincipal UserDetails userDetails,
                                      @RequestParam(defaultValue = "10") int topLimit) {
        User user = getAuthenticatedUser(userDetails);

        long totalPlays = playbackHistoryRepository.countByUser(user);

        List<Object[]> topRaw = playbackHistoryRepository.findTopPlayedByUser(
                user, PageRequest.of(0, topLimit));

        List<Map<String, Object>> topTracks = topRaw.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("trackPath", row[0]);
            m.put("title",     row[1]);
            m.put("artist",    row[2]);
            m.put("album",     row[3]);
            m.put("nasPathId", row[4]);
            m.put("playCount", row[5]);
            return m;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "totalPlays", totalPlays,
                "topTracks",  topTracks
        ));
    }

    @Operation(summary = "Artistas más escuchados del usuario")
    @GetMapping("/top-artists")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<?> getTopArtists(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestParam(defaultValue = "20") int limit) {
        User user = getAuthenticatedUser(userDetails);
        List<Object[]> raw = playbackHistoryRepository.findTopArtistsByUser(
                user, PageRequest.of(0, limit));
        List<Map<String, Object>> result = raw.stream().map(row -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("artist",    row[0]);
            m.put("playCount", row[1]);
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }
}
