package com.everload.everload.controller;

import com.everload.everload.model.Playlist;
import com.everload.everload.model.PlaylistTrack;
import com.everload.everload.model.User;
import com.everload.everload.repository.PlaylistRepository;
import com.everload.everload.repository.UserRepository;
import com.everload.everload.service.PlaylistImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
public class PlaylistActionsController {
    private final PlaylistRepository playlists;
    private final UserRepository users;
    private final PlaylistImportService importer;
    private final TransactionTemplate transactions;

    public record ImportRequest(String url, String name) {}

    private User user(UserDetails principal) {
        return users.findByUsername(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    @GetMapping("/{id}")
    public Playlist get(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return playlists.findReadable(id, user(principal))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/{id}/duplicate")
    public Playlist duplicate(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        User owner = user(principal);
        return transactions.execute(status -> {
            Playlist original = playlists.findReadable(id, owner)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            Playlist copy = Playlist.builder().user(owner).name(original.getName().substring(0, Math.min(190, original.getName().length())) + " (copy)").build();
            for (PlaylistTrack t : original.getTracks()) {
                copy.getTracks().add(PlaylistTrack.builder().playlist(copy).position(copy.getTracks().size())
                        .trackPath(t.getTrackPath()).title(t.getTitle()).artist(t.getArtist()).album(t.getAlbum())
                        .source(t.getSource()).nasPathId(t.getNasPathId()).durationSeconds(t.getDurationSeconds()).build());
            }
            return playlists.save(copy);
        });
    }

    @PostMapping("/import")
    public Map<String, Object> importPlaylist(@AuthenticationPrincipal UserDetails principal, @RequestBody ImportRequest request) {
        User owner = user(principal);
        if (request.name() == null || request.name().isBlank() || request.name().trim().length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid playlist name");
        }
        PlaylistImportService.Imported result;
        try {
            result = importer.read(request.url());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or unavailable playlist");
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Playlist provider unavailable");
        }
        Playlist saved = transactions.execute(status -> {
            Playlist playlist = Playlist.builder().user(owner).name(request.name().trim()).build();
            for (PlaylistTrack track : result.tracks()) {
                track.setPlaylist(playlist);
                track.setPosition(playlist.getTracks().size());
                playlist.getTracks().add(track);
            }
            return playlists.save(playlist);
        });
        return Map.of("playlist", saved, "skipped", result.skipped(), "spotifySource", result.spotifySource());
    }
}
