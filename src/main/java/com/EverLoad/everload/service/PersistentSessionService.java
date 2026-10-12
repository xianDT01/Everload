package com.everload.everload.service;

import com.everload.everload.dto.AuthResponse;
import com.everload.everload.model.PersistentSession;
import com.everload.everload.model.User;
import com.everload.everload.model.UserStatus;
import com.everload.everload.repository.PersistentSessionRepository;
import com.everload.everload.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PersistentSessionService {
    private static final String INVALID_SESSION = "Sesion invalida o expirada";
    private final PersistentSessionRepository repository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.auth.persistent-session-days:90}")
    private int sessionDays = 90;

    @Transactional
    public String create(String username) {
        User user = userRepository.findByUsername(username)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_SESSION));
        String secret = newSecret();
        PersistentSession session = new PersistentSession();
        session.setUserId(user.getId());
        session.setCredentialHash(hash(user.getPassword()));
        session.setTokenHash(hash(secret));
        session.setExpiresAt(Instant.now().plus(sessionDays, ChronoUnit.DAYS));
        repository.save(session);
        return secret;
    }

    @Transactional
    public AuthResponse refresh(String secret) {
        PersistentSession session = repository.findForRotation(hash(secret))
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalArgumentException(INVALID_SESSION));
        User user = userRepository.findById(session.getUserId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> hash(u.getPassword()).equals(session.getCredentialHash()))
                .orElseThrow(() -> new IllegalArgumentException(INVALID_SESSION));
        AuthResponse response = authService.refreshToken(user.getUsername());
        String nextSecret = newSecret();
        session.setTokenHash(hash(nextSecret));
        session.setExpiresAt(Instant.now().plus(sessionDays, ChronoUnit.DAYS));
        repository.save(session);
        response.setRefreshToken(nextSecret);
        return response;
    }

    @Transactional
    public void revoke(String secret) {
        if (secret != null && !secret.isBlank()) repository.deleteByTokenHash(hash(secret));
    }

    @Scheduled(fixedDelay = 86_400_000)
    @Transactional
    public void cleanupExpired() {
        repository.deleteExpired(Instant.now());
    }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
