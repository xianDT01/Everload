package com.EverLoad.everload.controller;

import com.EverLoad.everload.security.JwtUtil;
import com.EverLoad.everload.security.UserDetailsServiceImpl;
import com.EverLoad.everload.service.HlsStreamService;
import com.EverLoad.everload.service.MaintenanceService;
import com.EverLoad.everload.service.MusicService;
import com.EverLoad.everload.service.TokenRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the /api/music/stream endpoint forwards the "quality" parameter to
 * MusicService (the on-the-fly Ogg/Opus transcoding feature) and translates
 * service-level exceptions into the right HTTP status codes.
 */
@WebMvcTest(controllers = MusicController.class)
@AutoConfigureMockMvc(addFilters = false)
class MusicControllerTest {

    @MockBean
    JwtUtil jwtUtil;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    TokenRevocationService tokenRevocationService;

    @MockBean
    MaintenanceService maintenanceService;

    @MockBean
    MusicService musicService;

    @MockBean
    HlsStreamService hlsStreamService;

    @MockBean
    org.springframework.web.client.RestTemplate restTemplate;

    @Autowired
    MockMvc mvc;

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
}
