package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.*;
import com.EverLoad.everload.service.YtMusicService;
import com.EverLoad.everload.service.YtMusicTransportException;
import com.EverLoad.everload.service.YtStreamUnavailableException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * YouTube Music — anonymous browsing and playback only: search, discovery,
 * albums/artists, public playlists, radio mixes and stream resolution.
 * There is no library, no playlist mutation, no sign-in — every endpoint
 * here reaches only what an anonymous visitor could already see.
 *
 * <p>Talks to a single facade ({@link YtMusicService}); it has no idea how
 * many internal services or resolver strategies sit behind it.
 */
@Tag(name = "YouTube Music", description = "Búsqueda, exploración y reproducción anónima de YouTube Music")
@RestController
@RequestMapping("/api/ytmusic")
@PreAuthorize("hasAnyRole('ADMIN', 'NAS_USER', 'BASIC_USER')")
public class YtMusicController {

    private static final Logger log = LoggerFactory.getLogger(YtMusicController.class);

    /** YouTube video / browse / channel / playlist ids are short alphanumeric-ish tokens — never free text. */
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");

    private final YtMusicService ytMusicService;

    public YtMusicController(YtMusicService ytMusicService) {
        this.ytMusicService = ytMusicService;
    }

    // ── Search ────────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String query) {
        ResponseEntity<?> gate = checkEnabledAndQuery(query);
        if (gate != null) return gate;
        return handle(() -> Map.of("items", ytMusicService.search(query.trim())), "buscar \"" + query + "\"");
    }

    @GetMapping("/suggestions")
    public ResponseEntity<?> suggestions(@RequestParam String query) {
        ResponseEntity<?> gate = checkEnabledAndQuery(query);
        if (gate != null) return gate;
        return handle(() -> Map.of("items", ytMusicService.suggestions(query.trim())), "sugerencias \"" + query + "\"");
    }

    @GetMapping("/artist/resolve")
    public ResponseEntity<?> resolveArtistChannel(@RequestParam String name) {
        ResponseEntity<?> gate = checkEnabledAndQuery(name);
        if (gate != null) return gate;
        return handle(() -> {
            String channelId = ytMusicService.resolveArtistChannelId(name.trim());
            return channelId != null
                    ? Map.of("channelId", channelId)
                    : Map.of("channelId", "");
        }, "resolver artista \"" + name + "\"");
    }

    // ── Discover ──────────────────────────────────────────────────────

    @GetMapping("/discover/home")
    public ResponseEntity<?> discoverHome() {
        ResponseEntity<?> gate = checkEnabled();
        if (gate != null) return gate;
        return handle(ytMusicService::discoverHome, "cargar la página de inicio de YT Music");
    }

    @GetMapping("/discover/new-releases")
    public ResponseEntity<?> discoverNewReleases() {
        ResponseEntity<?> gate = checkEnabled();
        if (gate != null) return gate;
        return handle(ytMusicService::discoverNewReleases, "cargar los nuevos lanzamientos de YT Music");
    }

    @GetMapping("/discover/charts")
    public ResponseEntity<?> discoverCharts() {
        ResponseEntity<?> gate = checkEnabled();
        if (gate != null) return gate;
        return handle(ytMusicService::discoverCharts, "cargar los charts de YT Music");
    }

    @GetMapping("/discover/continuation")
    public ResponseEntity<?> discoverContinuation(@RequestParam String token) {
        ResponseEntity<?> gate = checkEnabled();
        if (gate != null) return gate;
        if (token == null || token.isBlank() || token.length() > 4000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Token de continuación inválido"));
        }
        return handle(() -> ytMusicService.discoverContinuation(token), "cargar más contenido");
    }

    @GetMapping("/album/{browseId}")
    public ResponseEntity<?> getAlbum(@PathVariable String browseId) {
        ResponseEntity<?> gate = checkEnabledAndId(browseId);
        if (gate != null) return gate;
        return handle(() -> ytMusicService.getAlbum(browseId), "cargar el álbum " + browseId);
    }

    @GetMapping("/artist/{channelId}")
    public ResponseEntity<?> getArtist(@PathVariable String channelId) {
        ResponseEntity<?> gate = checkEnabledAndId(channelId);
        if (gate != null) return gate;
        return handle(() -> ytMusicService.getArtist(channelId), "cargar el artista " + channelId);
    }

    // ── Playlists ─────────────────────────────────────────────────────

    @GetMapping("/playlist/{playlistId}")
    public ResponseEntity<?> getPlaylist(@PathVariable String playlistId) {
        ResponseEntity<?> gate = checkEnabledAndId(playlistId);
        if (gate != null) return gate;
        return handle(() -> {
            YtPlaylistSummaryDto summary = ytMusicService.getPlaylistSummary(playlistId);
            List<YtTrackDto> entries = ytMusicService.getPlaylistEntries(playlistId);
            return Map.of(
                    "playlistId", summary.getPlaylistId(),
                    "title", summary.getTitle(),
                    "thumbnailUrl", summary.getThumbnailUrl() == null ? "" : summary.getThumbnailUrl(),
                    "tracks", entries
            );
        }, "cargar la playlist " + playlistId);
    }

    // ── Mixes / radio ─────────────────────────────────────────────────

    @GetMapping("/mix/{videoId}")
    public ResponseEntity<?> startMix(@PathVariable String videoId) {
        ResponseEntity<?> gate = checkEnabledAndId(videoId);
        if (gate != null) return gate;
        return handle(() -> Map.of("items", ytMusicService.startMix(videoId)), "iniciar la radio de " + videoId);
    }

    // ── Stream resolution ─────────────────────────────────────────────

    @GetMapping("/stream/{videoId}")
    public ResponseEntity<?> getStream(@PathVariable String videoId) {
        ResponseEntity<?> gate = checkEnabledAndId(videoId);
        if (gate != null) return gate;
        try {
            return ResponseEntity.ok(ytMusicService.getStream(videoId));
        } catch (YtStreamUnavailableException e) {
            log.info("Stream no disponible para {}: {}", videoId, e.resolverFailures());
            return ResponseEntity.status(409).body(Map.of(
                    "error", "No se pudo reproducir este contenido (restringido, bloqueado por región, eliminado o no disponible).",
                    "videoId", e.videoId(),
                    "details", e.resolverFailures()
            ));
        } catch (YtMusicTransportException e) {
            log.warn("Fallo de transporte resolviendo stream de {}: {}", videoId, e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "Fallo comunicando con YouTube Music: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado resolviendo stream de {}", videoId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Error inesperado al resolver el stream"));
        }
    }

    // ── Shared validation / error handling ────────────────────────────

    @GetMapping("/stream/{videoId}/audio")
    public void streamAudio(@PathVariable String videoId,
                            @RequestHeader(value = "Range", required = false) String rangeHeader,
                            HttpServletResponse response) {
        ResponseEntity<?> gate = checkEnabledAndId(videoId);
        if (gate != null) {
            response.setStatus(gate.getStatusCode().value());
            return;
        }
        try {
            ytMusicService.streamAudioToResponse(videoId, rangeHeader, response);
        } catch (YtStreamUnavailableException e) {
            log.info("Stream proxy no disponible para {}: {}", videoId, e.resolverFailures());
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        } catch (YtMusicTransportException e) {
            log.warn("Fallo de transporte en proxy de stream {}: {}", videoId, e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        } catch (Exception e) {
            log.error("Error inesperado en proxy de stream {}", videoId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<?> checkEnabled() {
        if (!ytMusicService.isEnabled()) {
            return ResponseEntity.status(503).body(Map.of("error", "YouTube Music está deshabilitado en este servidor"));
        }
        return null;
    }

    private ResponseEntity<?> checkEnabledAndQuery(String query) {
        ResponseEntity<?> gate = checkEnabled();
        if (gate != null) return gate;
        if (query == null || query.isBlank() || query.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "Consulta inválida"));
        }
        return null;
    }

    private ResponseEntity<?> checkEnabledAndId(String id) {
        ResponseEntity<?> gate = checkEnabled();
        if (gate != null) return gate;
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Identificador inválido"));
        }
        return null;
    }

    private interface Action<T> {
        T run();
    }

    private <T> ResponseEntity<?> handle(Action<T> action, String operationDescription) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (YtMusicTransportException e) {
            log.warn("Fallo al {}: {}", operationDescription, e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "Fallo comunicando con YouTube Music: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error inesperado al {}", operationDescription, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Error inesperado al " + operationDescription));
        }
    }
}
