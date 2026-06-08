package com.EverLoad.everload.controller;

import com.EverLoad.everload.security.JwtUtil;
import com.EverLoad.everload.security.UserDetailsServiceImpl;
import com.EverLoad.everload.service.MaintenanceService;
import com.EverLoad.everload.service.NasYtDlpService;
import com.EverLoad.everload.service.TokenRevocationService;
import com.EverLoad.everload.service.NasYtDlpService.YtDlpJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers the social-media "save to NAS" endpoint added this session
 * (POST /api/nas/ytdlp/queue-url) — in particular the domain allowlist that
 * keeps the feature scoped to Facebook/Instagram/TikTok/Twitter links.
 */
@WebMvcTest(controllers = NasYtDlpController.class)
@AutoConfigureMockMvc(addFilters = false)
class NasYtDlpControllerTest {

    @MockBean
    JwtUtil jwtUtil;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    TokenRevocationService tokenRevocationService;

    @MockBean
    MaintenanceService maintenanceService;

    @MockBean
    NasYtDlpService service;

    @Autowired
    MockMvc mvc;

    @Test
    void queueUrl_blankUrl_returnsBadRequest() throws Exception {
        mvc.perform(post("/api/nas/ytdlp/queue-url")
                        .param("url", "")
                        .param("nasPathId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("URL requerida"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=abc123",
            "https://example.com/video.mp4",
            "https://vimeo.com/12345"
    })
    void queueUrl_disallowedDomain_returnsBadRequest(String url) throws Exception {
        mvc.perform(post("/api/nas/ytdlp/queue-url")
                        .param("url", url)
                        .param("nasPathId", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Dominio no permitido"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.facebook.com/reel/12345",
            "https://fb.watch/abcDEF/",
            "https://www.instagram.com/reel/xyz/",
            "https://www.tiktok.com/@user/video/123",
            "https://twitter.com/user/status/123",
            "https://x.com/user/status/123"
    })
    void queueUrl_allowedDomain_queuesJobAndReturnsId(String url) throws Exception {
        when(service.queueUrl(eq(url), eq("Mi video"), eq(5L), eq("Reels")))
                .thenReturn("job-123");

        mvc.perform(post("/api/nas/ytdlp/queue-url")
                        .param("url", url)
                        .param("title", "Mi video")
                        .param("nasPathId", "5")
                        .param("subPath", "Reels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"));

        verify(service).queueUrl(url, "Mi video", 5L, "Reels");
    }

    @Test
    void getStatus_unknownJob_returnsNotFound() throws Exception {
        when(service.getJob("missing")).thenReturn(null);

        mvc.perform(get("/api/nas/ytdlp/status/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStatus_knownJob_returnsJobPayload() throws Exception {
        YtDlpJob job = new YtDlpJob("job-123", null, "Mi video", 5L, "Reels", "video");
        job.status = YtDlpJob.Status.RUNNING;
        job.progress = 42;
        when(service.getJob("job-123")).thenReturn(job);

        mvc.perform(get("/api/nas/ytdlp/status/job-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-123"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.progress").value(42));
    }

    @Test
    void queue_defaultsFormatToMp3_whenOmitted() throws Exception {
        when(service.queue(eq("abc123"), anyString(), eq(1L), anyString(), eq("mp3")))
                .thenReturn("job-mp3");

        mvc.perform(post("/api/nas/ytdlp/queue")
                        .param("videoId", "abc123")
                        .param("nasPathId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-mp3"));

        verify(service).queue("abc123", "", 1L, "", "mp3");
    }
}
