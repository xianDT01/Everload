package com.everload.everload.service;

import com.everload.everload.dto.AuthResponse;
import com.everload.everload.model.PersistentSession;
import com.everload.everload.model.User;
import com.everload.everload.model.UserStatus;
import com.everload.everload.repository.PersistentSessionRepository;
import com.everload.everload.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersistentSessionServiceTest {
    private final PersistentSessionRepository sessions = mock(PersistentSessionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final PersistentSessionService service = new PersistentSessionService(sessions, users, auth);
    private User user;
    private PersistentSession saved;

    @BeforeEach
    void setup() {
        user = User.builder().id(10L).username("listener").password("password-hash")
                .status(UserStatus.ACTIVE).build();
        when(users.findByUsername("listener")).thenReturn(Optional.of(user));
        when(users.findById(10L)).thenReturn(Optional.of(user));
        when(sessions.save(any())).thenAnswer(call -> saved = call.getArgument(0));
        when(auth.refreshToken("listener")).thenReturn(AuthResponse.builder().token("access").build());
    }
    @Test void creationStoresOnlyHashWithExpiryAndAccountId() {
        String secret = service.create("listener");
        assertEquals(43, secret.length());
        assertEquals(64, saved.getTokenHash().length());
        assertNotEquals(secret, saved.getTokenHash());
        assertNotEquals(user.getPassword(), saved.getCredentialHash());
        assertEquals(10L, saved.getUserId());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now().plusSeconds(89 * 86400)));
    }
    @Test void refreshRotatesSecretAndIssuesAccessToken() {
        String secret = service.create("listener");
        String oldHash = saved.getTokenHash();
        when(sessions.findForRotation(oldHash)).thenReturn(Optional.of(saved));
        AuthResponse result = service.refresh(secret);
        assertEquals("access", result.getToken());
        assertNotEquals(secret, result.getRefreshToken());
        assertNotEquals(oldHash, saved.getTokenHash());
        verify(auth).refreshToken("listener");
    }
    @Test void expiredSessionCannotIssueAccessToken() {
        String secret = service.create("listener");
        saved.setExpiresAt(Instant.now().minusSeconds(1));
        when(sessions.findForRotation(saved.getTokenHash())).thenReturn(Optional.of(saved));
        assertThrows(IllegalArgumentException.class, () -> service.refresh(secret));
        verifyNoInteractions(auth);
    }
    @Test void deactivatedOrDeletedAccountCannotRefresh() {
        String secret = service.create("listener");
        when(sessions.findForRotation(saved.getTokenHash())).thenReturn(Optional.of(saved));
        user.setStatus(UserStatus.REJECTED);
        assertThrows(IllegalArgumentException.class, () -> service.refresh(secret));
        when(users.findById(10L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.refresh(secret));
        verifyNoInteractions(auth);
    }
    @Test void passwordChangeInvalidatesSavedSession() {
        String secret = service.create("listener");
        when(sessions.findForRotation(saved.getTokenHash())).thenReturn(Optional.of(saved));
        user.setPassword("different-password-hash");
        assertThrows(IllegalArgumentException.class, () -> service.refresh(secret));
        verifyNoInteractions(auth);
    }
    @Test void revokedOrUnknownSecretCannotRefresh() {
        when(sessions.findForRotation(anyString())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.refresh("unknown"));
        verifyNoInteractions(auth);
    }
    @Test void logoutRevokesOnlyTheProvidedSessionHash() {
        String secret = service.create("listener");
        String hash = saved.getTokenHash();
        service.revoke(secret);
        verify(sessions).deleteByTokenHash(hash);
        service.revoke(null);
        verify(sessions, times(1)).deleteByTokenHash(anyString());
    }
}
