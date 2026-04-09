package com.EverLoad.everload.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Stores revoked JWT IDs so that logged-out tokens are rejected
 * even before their natural expiry.
 */
@Entity
@Table(name = "revoked_tokens", indexes = {
    @Index(name = "idx_revoked_jti", columnList = "jti", unique = true),
    @Index(name = "idx_revoked_expires", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String jti;

    /** When the original JWT expires — used for automatic cleanup. */
    @Column(nullable = false)
    private Instant expiresAt;
}