package com.EverLoad.everload.controller;

import com.EverLoad.everload.model.FavoriteTrack;
import com.EverLoad.everload.model.PlaybackHistory;
import com.EverLoad.everload.model.User;
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
import java.util.stream.Collectors;

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
                                            @RequestBody FavoriteTrack dto) {
        User user = getAuthenticatedUser(userDetails);
        var existing = favoriteTrackRepository.findByUserAndTrackPathAndNasPathId(user, dto.getTrackPath(), dto.getNasPathId());
        
        if (existing.isPresent()) {
            favoriteTrackRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of("message", "Removed from favorites", "isFavorite", false));
        } else {
            dto.setId(null); // Ensure INSERT, not UPDATE of a deleted row
            dto.setUser(user);
            favoriteTrackRepository.save(dto);
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
                                        @RequestBody PlaybackHistory dto) {
        User user = getAuthenticatedUser(userDetails);
        dto.setUser(user);
        playbackHistoryRepository.save(dto);
        return ResponseEntity.ok(Map.of("message", "History recorded"));
    }
}
