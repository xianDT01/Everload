package com.EverLoad.everload.controller;

import com.EverLoad.everload.dto.YtPlaylistSummaryDto;
import com.EverLoad.everload.dto.YtStreamInfoDto;
import com.EverLoad.everload.dto.YtTrackDto;
import com.EverLoad.everload.security.JwtUtil;
import com.EverLoad.everload.security.UserDetailsServiceImpl;
import com.EverLoad.everload.service.MaintenanceService;
import com.EverLoad.everload.service.TokenRevocationService;
import com.EverLoad.everload.service.YtMusicService;
import com.EverLoad.everload.service.YtMusicTransportException;
import com.EverLoad.everload.service.YtStreamUnavailableException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = YtMusicController.class)
@AutoConfigureMockMvc(addFilters = false)
class YtMusicControllerTest {

    @MockBean
    JwtUtil jwtUtil;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    TokenRevocationService tokenRevocationService;

    @MockBean
    MaintenanceService maintenanceService;

    @MockBean
    YtMusicService ytMusicService;

    @Autowired
    MockMvc mvc;

    @Test
    void search_trimsQueryAndWrapsItems() throws Exception {
        when(ytMusicService.isEnabled()).thenReturn(true);
        when(ytMusicService.search("radiohead")).thenReturn(List.of(
                YtTrackDto.builder().videoId("abc123").title("Everything In Its Right Place").build()
        ));

        mvc.perform(get("/api/ytmusic/search").param("query", "  radiohead  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].videoId").value("abc123"))
                .andExpect(jsonPath("$.items[0].title").value("Everything In Its Right Place"));

        verify(ytMusicService).search(eq("radiohead"));
    }

    @Test
    void search_rejectsBlankQueryBeforeCallingSearch() throws Exception {
        when(ytMusicService.isEnabled()).thenReturn(true);

        mvc.perform(get("/api/ytmusic/search").param("query", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Consulta inválida"));

        verify(ytMusicService).isEnabled();
        verifyNoMoreInteractions(ytMusicService);
    }

    @Test
    void endpointsReturn503WhenModuleIsDisabled() throws Exception {
        when(ytMusicService.isEnabled()).thenReturn(false);

        mvc.perform(get("/api/ytmusic/search").param("query", "rosalia"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("YouTube Music está deshabilitado en este servidor"));

        verifyNoInteractionsExceptEnabled();
    }

    @Test
    void playlistCombinesSummaryAndEntries() throws Exception {
        when(ytMusicService.isEnabled()).thenReturn(true);
        when(ytMusicService.getPlaylistSummary("PL123")).thenReturn(
                YtPlaylistSummaryDto.builder().playlistId("PL123").title("Favorites").thumbnailUrl(null).build()
        );
        when(ytMusicService.getPlaylistEntries("PL123")).thenReturn(List.of(
                YtTrackDto.builder().videoId("song123").title("Track").build()
        ));

        mvc.perform(get("/api/ytmusic/playlist/PL123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlistId").value("PL123"))
                .andExpect(jsonPath("$.title").value("Favorites"))
                .andExpect(jsonPath("$.thumbnailUrl").value(""))
                .andExpect(jsonPath("$.tracks[0].videoId").value("song123"));
    }

    @Test
    void streamReturns409WhenNoResolverCanProduceAUrl() throws Exception {
        when(ytMusicService.isEnabled()).thenReturn(true);
        when(ytMusicService.getStream("video_123")).thenThrow(
                new YtStreamUnavailableException("video_123", List.of("botguard failed", "yt-dlp failed"))
        );

        mvc.perform(get("/api/ytmusic/stream/video_123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.videoId").value("video_123"))
                .andExpect(jsonPath("$.details[0]").value("botguard failed"));
    }

    @Test
    void streamReturns502ForTransportFailures() throws Exception {
        when(ytMusicService.isEnabled()).thenReturn(true);
        when(ytMusicService.getStream("video_123")).thenThrow(new YtMusicTransportException("timeout"));

        mvc.perform(get("/api/ytmusic/stream/video_123"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error", containsString("timeout")));
    }

    @Test
    void streamReturnsResolvedInfo() throws Exception {
        when(ytMusicService.isEnabled()).thenReturn(true);
        when(ytMusicService.getStream("video_123")).thenReturn(
                YtStreamInfoDto.builder().url("https://example.test/audio").format("m4a").resolvedBy("test").build()
        );

        mvc.perform(get("/api/ytmusic/stream/video_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.test/audio"))
                .andExpect(jsonPath("$.format").value("m4a"))
                .andExpect(jsonPath("$.resolvedBy").value("test"));
    }

    @Test
    void audioProxyReturns503AndRestoresInterruptFlag() throws Exception {
        when(ytMusicService.isEnabled()).thenReturn(true);
        doThrow(new InterruptedException("cancelled"))
                .when(ytMusicService).streamAudioToResponse(
                        eq("video_123"), isNull(), any(HttpServletResponse.class));

        mvc.perform(get("/api/ytmusic/stream/video_123/audio"))
                .andExpect(status().isServiceUnavailable());

        assertTrue(Thread.interrupted());
    }

    private void verifyNoInteractionsExceptEnabled() {
        verify(ytMusicService).isEnabled();
    }
}
