package com.EverLoad.everload.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Records every significant admin action for accountability. */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_admin", columnList = "adminUsername"),
    @Index(name = "idx_audit_ts",    columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Username of the admin who performed the action. */
    @Column(nullable = false)
    private String adminUsername;

    /**
     * Action type. Examples:
     *   USER_APPROVED, USER_REJECTED, USER_ROLE_CHANGED, USER_DELETED,
     *   MESSAGE_DELETED, GROUP_DELETED, MEMBER_KICKED, CONFIG_UPDATED
     */
    @Column(nullable = false, length = 64)
    private String action;

    /** Type of entity affected: "User", "ChatMessage", "ChatGroup", "Config", etc. */
    @Column(length = 64)
    private String targetEntity;

    /** ID or name of the affected entity. */
    @Column(length = 256)
    private String targetId;

    /** Human-readable extra detail (optional). */
    @Column(length = 512)
    private String detail;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) timestamp = LocalDateTime.now();
    }
}