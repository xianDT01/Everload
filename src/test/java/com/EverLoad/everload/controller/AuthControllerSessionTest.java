package com.everload.everload.controller;

import com.everload.everload.dto.AuthResponse;
import com.everload.everload.security.JwtUtil;
import com.everload.everload.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerSessionTest {
    private final AuthService auth = mock(AuthService.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final TokenRevocationService revoked = mock(TokenRevocationService.class);
    private final PresenceService presence = mock(PresenceService.class);
    private final PersistentSessionService sessions = mock(PersistentSessionService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, jwt, revoked, presence, sessions)).build();
        when(auth.login(any())).thenReturn(AuthResponse.builder().token("access").username("listener").build());
        when(sessions.create("listener")).thenReturn("persistent");
    }
    @Test void androidLoginRequestsPersistentSession() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"listener\",\"password\":\"password\",\"rememberMe\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").value("persistent"));
        verify(sessions).create("listener");
    }
    @Test void existingWebLoginDoesNotCreatePersistentSession() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"listener\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").doesNotExist());
        verify(sessions, never()).create(anyString());
    }
    @Test void webLogoutStillAcceptsEmptyObjectAndRevokesAccessToken() throws Exception {
        Instant expiry = Instant.now().plusSeconds(3600);
        when(jwt.extractJti("access")).thenReturn("id");
        when(jwt.extractExpiration("access")).thenReturn(expiry);
        when(jwt.extractUsername("access")).thenReturn("listener");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer access")
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        verify(revoked).revoke("id", expiry);
    }
    @Test void androidLogoutRevokesItsPersistentSession() throws Exception {
        mvc.perform(post("/api/auth/logout").contentType("application/json")
                .content("{\"refreshToken\":\"persistent\"}"))
                .andExpect(status().isOk());
        verify(sessions).revoke("persistent");
    }
    @Test void invalidPersistentSessionReturnsUnauthorized() throws Exception {
        when(sessions.refresh("invalid")).thenThrow(new IllegalArgumentException("invalid"));
        mvc.perform(post("/api/auth/session/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void refreshRequiresANonBlankCredential() throws Exception {
        mvc.perform(post("/api/auth/session/refresh").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sessions);
    }
}
