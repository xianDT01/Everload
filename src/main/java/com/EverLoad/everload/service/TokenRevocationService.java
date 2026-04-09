package com.EverLoad.everload.service;

import com.EverLoad.everload.model.RevokedToken;
import com.EverLoad.everload.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RevokedTokenRepository repository;

    /** Revokes a token by storing its jti. Called on logout. */
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) return;
        if (!repository.existsByJti(jti)) {
            repository.save(RevokedToken.builder()
                    .jti(jti)
                    .expiresAt(expiresAt)
                    .build());
        }
    }

    /** Returns true if the jti has been explicitly revoked (logout). */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return false;
        return repository.existsByJti(jti);
    }

    /** Runs every hour to purge expired revoked tokens from the DB. */
    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupExpired() {
        repository.deleteExpiredTokens(Instant.now());
        log.debug("Cleaned up expired revoked tokens");
    }
}