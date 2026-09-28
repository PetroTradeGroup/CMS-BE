package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.UserSyncStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Local shadow of a Keycloak-managed attendant account. Written durably before the Keycloak
 * admin call is attempted, so a registration survives a Keycloak outage instead of being lost —
 * {@link com.couponnumbergenerator.service.impl.AttendantSyncSweep} retries anything not SYNCED.
 */
@Entity
@Table(name = "app_users", indexes = {
        @Index(name = "idx_app_users_sync_status", columnList = "sync_status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_id", length = 36)
    private String keycloakId;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    /** Always ATTENDANT for now — the only role {@code AttendantService} can produce. */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "ATTENDANT";

    @Column(name = "location_code", length = 10)
    private String locationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private UserSyncStatus syncStatus;

    @Column(name = "sync_attempts", nullable = false)
    @Builder.Default
    private int syncAttempts = 0;

    @Column(name = "last_sync_error", length = 500)
    private String lastSyncError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markSynced(String keycloakId) {
        this.keycloakId = keycloakId;
        this.syncStatus = UserSyncStatus.SYNCED;
        this.lastSyncError = null;
    }

    public void markFailed(String error) {
        this.syncStatus = UserSyncStatus.FAILED;
        this.syncAttempts = this.syncAttempts + 1;
        this.lastSyncError = error;
    }
}
