package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.SecurityAuditOutcome;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit record of one security-relevant request: denied (401/403), or allowed and
 * mutating. Rows are insert-only — never updated or deleted (same compliance requirement as
 * {@link CouponMovement}).
 */
@Entity
@Table(name = "security_audit_log", indexes = {
        @Index(name = "idx_security_audit_created_at", columnList = "created_at")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for {@link SecurityAuditOutcome#UNAUTHENTICATED} — no valid token means no principal to name. */
    @Column(name = "principal", updatable = false, length = 100)
    private String principal;

    /** Comma-joined granted authorities (e.g. "ROLE_ADMIN,ROLE_AUDITOR"), null when there are none. */
    @Column(name = "roles", updatable = false, length = 255)
    private String roles;

    @Column(name = "http_method", nullable = false, updatable = false, length = 10)
    private String httpMethod;

    @Column(name = "path", nullable = false, updatable = false, length = 255)
    private String path;

    @Column(name = "status_code", nullable = false, updatable = false)
    private int statusCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false, length = 20)
    private SecurityAuditOutcome outcome;

    @Column(name = "reason", updatable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
