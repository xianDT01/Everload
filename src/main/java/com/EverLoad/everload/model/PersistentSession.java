package com.everload.everload.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "persistent_sessions", indexes = @Index(columnList = "expires_at"))
@Getter
@Setter
@NoArgsConstructor
public class PersistentSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    // Keep the ID, not a username: deleting and recreating an account cannot restore its sessions.
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String credentialHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
