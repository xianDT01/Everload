package com.everload.everload.controller;

import com.everload.everload.security.JwtUtil;
import com.everload.everload.security.UserDetailsServiceImpl;
import com.everload.everload.service.HlsStreamService;
import com.everload.everload.service.MaintenanceService;
import com.everload.everload.service.MusicService;
import com.everload.everload.service.MetadataSnapshotService;
import com.everload.everload.service.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Verifies the /api/music/stream endpoint forwards the "quality" parameter to
 * MusicService (the on-the-fly Ogg/Opus transcoding feature) and translates
 * service-level exceptions into the right HTTP status codes.
 */
@WebMvcTest(controllers = MusicController.class)
@AutoConfigureMockMvc(addFilters = false)
class MusicControllerTest {

    @MockitoBean
    JwtUtil jwtUtil;

    @MockitoBean
    UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    TokenRevocationService tokenRevocationService;

    @MockitoBean
    MaintenanceService maintenanceService;

    @MockitoBean
    MusicService musicService;

    @MockitoBean
    MetadataSnapshotService metadataSnapshotService;

    @MockitoBean
    HlsStreamService hlsStreamService;

    @MockitoBean
    org.springframework.web.client.RestTemplate restTemplate;

    @Autowired
    MockMvc mvc;

    @Autowired
    MusicController controller;

    @Test
    void libraryTracksValidatesPagingBounds() throws Exception {
        mvc.perform(get("/api/music/library-tracks").param("pathId", "1").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/music/library-tracks").param("pathId", "1").param("size", "10000"))
                .andExpect(status().isBadRequest());
        verify(musicService, never()).getLibraryTracks(anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    void libraryTracksForwardsPageAndSearch() throws Exception {
        when(musicService.getLibraryTracks(1L, 2, 100, "Tacabro"))
                .thenReturn(java.util.Map.of("tracks", java.util.List.of(), "hasMore", true));
        mvc.perform(get("/api/music/library-tracks").param("pathId", "1").param("page", "2").param("query", "Tacabro"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void libraryTracksForwardsSortAndRejectsUnsupportedValues() throws Exception {
        when(musicService.getLibraryTracks(1L, 2, 100, "", "artist"))
                .thenReturn(java.util.Map.of("tracks", java.util.List.of(), "hasMore", true));
        mvc.perform(get("/api/music/library-tracks").param("pathId", "1").param("page", "2").param("sort", "artist"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/api/music/library-tracks").param("pathId", "1").param("sort", "unsupported"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cleanLyricsTermRemovesVideoAndFeaturedArtistSuffixes() {
        assertEquals("Song", ReflectionTestUtils.invokeMethod(
                controller, "cleanLyricsTerm", "Song (Official Music Video)"));
        assertEquals("Song", ReflectionTestUtils.invokeMethod(
                controller, "cleanLyricsTerm", "Song - Official Audio 2026"));
        assertEquals("Song", ReflectionTestUtils.invokeMethod(
                controller, "cleanLyricsTerm", "Song [ft. Guest]"));
    }

    @Test
    void streamAudio_defaultsQualityToOriginal_whenParamOmitted() throws Exception {
        mvc.perform(get("/api/music/stream").param("pathId", "1").param("subPath", "song.mp3"))
                .andExpect(status().isOk());

        verify(musicService).streamAudioToResponse(eq(1L), eq("song.mp3"), isNull(), eq("original"), any());
    }

    @Test
    void streamAudio_forwardsExplicitQualityParam() throws Exception {
        mvc.perform(get("/api/music/stream")
                        .param("pathId", "1")
                        .param("subPath", "song.flac")
                        .param("quality", "low"))
                .andExpect(status().isOk());

        verify(musicService).streamAudioToResponse(eq(1L), eq("song.flac"), isNull(), eq("low"), any());
    }

    @Test
    void streamAudio_forwardsRangeHeader() throws Exception {
        mvc.perform(get("/api/music/stream")
                        .param("pathId", "1")
                        .param("subPath", "song.mp3")
                        .header("Range", "bytes=0-1023"))
                .andExpect(status().isOk());

        verify(musicService).streamAudioToResponse(eq(1L), eq("song.mp3"), eq("bytes=0-1023"), eq("original"), any());
    }

    @Test
    void streamAudio_securityExceptionFromService_returns403() throws Exception {
        doThrow(new SecurityException("forbidden"))
                .when(musicService).streamAudioToResponse(anyLong(), anyString(), any(), anyString(), any());

        mvc.perform(get("/api/music/stream").param("pathId", "1").param("subPath", "song.mp3"))
                .andExpect(status().isForbidden());
    }

    @Test
    void streamAudio_illegalArgumentFromService_returns400() throws Exception {
        doThrow(new IllegalArgumentException("not found"))
                .when(musicService).streamAudioToResponse(anyLong(), anyString(), any(), anyString(), any());

        mvc.perform(get("/api/music/stream").param("pathId", "1").param("subPath", "missing.mp3"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamAudio_unexpectedExceptionFromService_returns500() throws Exception {
        doThrow(new RuntimeException("boom"))
                .when(musicService).streamAudioToResponse(anyLong(), anyString(), any(), anyString(), any());

        mvc.perform(get("/api/music/stream").param("pathId", "1").param("subPath", "song.mp3"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void artistImage_providerFailure_returnsValidNotFoundResult() throws Exception {
        when(musicService.lookupArtistImage("Unknown Artist")).thenThrow(new RuntimeException("provider unavailable"));

        mvc.perform(get("/api/music/artist-image").param("artist", "Unknown Artist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false));
    }

    @Test
    void coverArt_missingArtwork_returns404() throws Exception {
        when(musicService.getCoverArt(1L, "song.mp3")).thenReturn(new byte[0]);

        mvc.perform(get("/api/music/cover").param("pathId", "1").param("subPath", "song.mp3"))
                .andExpect(status().isNotFound());
    }

    @Test
    void folderCoverArt_missingArtwork_returns404() throws Exception {
        when(musicService.getFolderCoverArt(1L, "album")).thenReturn(new byte[0]);

        mvc.perform(get("/api/music/folder-cover").param("pathId", "1").param("subPath", "album"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchMusicCapsLimitAndMapsSecurityAndInputErrors() throws Exception {
        mvc.perform(get("/api/music/search").param("pathId", "1").param("query", "song")
                        .param("limit", "900"))
                .andExpect(status().isOk());
        verify(musicService).searchMusic(1L, null, "song", 500);

        when(musicService.searchMusic(1L, null, "forbidden", 200))
                .thenThrow(new SecurityException("no access"));
        mvc.perform(get("/api/music/search").param("pathId", "1").param("query", "forbidden"))
                .andExpect(status().isForbidden());

        when(musicService.searchMusic(1L, null, "invalid", 200))
                .thenThrow(new IllegalArgumentException("bad query"));
        mvc.perform(get("/api/music/search").param("pathId", "1").param("query", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void youtubeMetadataValidatesQueryAndMapsLookupFailure() throws Exception {
        mvc.perform(get("/api/music/youtube-metadata").param("query", " "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/music/youtube-metadata").param("query", "a".repeat(301)))
                .andExpect(status().isBadRequest());

        when(musicService.lookupYoutubeMetadataMap("song")).thenThrow(new IllegalStateException("offline"));
        mvc.perform(get("/api/music/youtube-metadata").param("query", "song"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error al buscar en YouTube: offline"));
    }

    @Test
    void metadataUpdateRefreshesSnapshotAndTranslatesServiceErrors() throws Exception {
        String payload = """
                {"pathId":1,"relativePath":"song.mp3","title":"Title","artist":"Artist","album":"Album","year":"2026"}
                """;
        mvc.perform(put("/api/music/metadata").contentType("application/json").content(payload))
                .andExpect(status().isOk());
        verify(musicService).updateMetadata(1L, "song.mp3", "Title", "Artist", "Album", "2026");
        verify(metadataSnapshotService).refreshTrack(1L, "song.mp3", "Title", "Artist", "Album");

        doThrow(new SecurityException("forbidden")).when(musicService)
                .updateMetadata(1L, "song.mp3", "Title", "Artist", "Album", "2026");
        mvc.perform(put("/api/music/metadata").contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
    }
}
