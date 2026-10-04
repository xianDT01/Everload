package com.everload.everload.controller;

import com.everload.everload.model.FavoriteTrack;
import com.everload.everload.model.PlaybackHistory;
import com.everload.everload.model.User;
import com.everload.everload.dto.FavoriteTrackRequest;
import com.everload.everload.dto.PlaybackHistoryRequest;
import com.everload.everload.dto.CommunityDiscoverResponse;
import com.everload.everload.repository.FavoriteTrackRepository;
import com.everload.everload.repository.PlaybackHistoryRepository;
import com.everload.everload.repository.UserRepository;
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

    private static final String MESSAGE_KEY = "message";
    private static final String IS_FAVORITE_KEY = "isFavorite";

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
    public ResponseEntity<Object> toggleFavorite(@AuthenticationPrincipal UserDetails userDetails,
                                            @RequestBody FavoriteTrackRequest request) {
        User user = getAuthenticatedUser(userDetails);
        var existing = favoriteTrackRepository.findByUserAndTrackPathAndNasPathId(user, request.trackPath(), request.nasPathId());
        
        if (existing.isPresent()) {
            favoriteTrackRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Removed from favorites", IS_FAVORITE_KEY, false));
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
            return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Added to favorites", IS_FAVORITE_KEY, true));
        }
    }

    @Operation(summary = "Comprobar si una pista es favorita")
    @GetMapping("/favorites/check")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> checkFavorite(@AuthenticationPrincipal UserDetails userDetails,
                                           @RequestParam String trackPath,
                                           @RequestParam Long nasPathId) {
        User user = getAuthenticatedUser(userDetails);
        boolean isFav = favoriteTrackRepository.existsByUserAndTrackPathAndNasPathId(user, trackPath, nasPathId);
        return ResponseEntity.ok(Map.of(IS_FAVORITE_KEY, isFav));
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
    public ResponseEntity<Object> addHistory(@AuthenticationPrincipal UserDetails userDetails,
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
        return ResponseEntity.ok(Map.of(MESSAGE_KEY, "History recorded"));
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    @Operation(summary = "Estadísticas de escucha del usuario")
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<Object> getStats(@AuthenticationPrincipal UserDetails userDetails,
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
    public ResponseEntity<Object> getTopArtists(@AuthenticationPrincipal UserDetails userDetails,
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

    @Operation(summary = "Tendencias musicales agregadas de la comunidad")
    @GetMapping("/community-discover")
    @PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
    public ResponseEntity<CommunityDiscoverResponse> getCommunityDiscover(
            @RequestParam(defaultValue = "40") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<CommunityDiscoverResponse.ArtistTrend> artists = playbackHistoryRepository
                .findCommunityTopArtists(PageRequest.of(0, safeLimit)).stream()
                .map(row -> new CommunityDiscoverResponse.ArtistTrend(
                        (String) row[0], ((Number) row[1]).longValue()))
                .toList();
        List<CommunityDiscoverResponse.TrackTrend> tracks = playbackHistoryRepository
                .findCommunityTopTracks(PageRequest.of(0, safeLimit)).stream()
                .map(row -> new CommunityDiscoverResponse.TrackTrend(
                        (String) row[0], (String) row[1], (String) row[2], ((Number) row[3]).longValue()))
                .toList();
        return ResponseEntity.ok(new CommunityDiscoverResponse(artists, tracks));
    }
}
