package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.enums.UserSyncStatus;
import com.couponnumbergenerator.model.AppUser;
import com.couponnumbergenerator.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodic retry queue for attendants whose Keycloak sync didn't complete at registration time —
 * a PENDING/FAILED {@link AppUser} row IS the queue entry, so nothing extra is persisted. Same
 * shape as {@code ErpRedemptionSync}'s retry sweep for ERP posting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendantSyncSweep {

    private static final int RETRY_BATCH_SIZE = 50;
    private static final List<UserSyncStatus> UNSYNCED = List.of(UserSyncStatus.PENDING, UserSyncStatus.FAILED);

    private final AppUserRepository appUserRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    @Scheduled(fixedDelayString = "${app.keycloak.retry-interval-ms:300000}",
            initialDelayString = "${app.keycloak.retry-interval-ms:300000}")
    public void retryUnsyncedAttendants() {
        List<AppUser> unsynced = appUserRepository
                .findBySyncStatusIn(UNSYNCED, PageRequest.of(0, RETRY_BATCH_SIZE, Sort.by("createdAt")))
                .getContent();
        if (unsynced.isEmpty()) {
            return;
        }
        log.info("Retrying Keycloak sync for {} attendant(s)", unsynced.size());
        unsynced.forEach(this::trySync);
    }

    private void trySync(AppUser appUser) {
        try {
            KeycloakAdminClient.SyncResult result = keycloakAdminClient.syncAttendant(appUser);
            appUser.markSynced(result.keycloakId());
            log.info("Synced attendant '{}' to Keycloak on retry", appUser.getUsername());
        } catch (Exception ex) {
            appUser.markFailed(ex.getMessage());
            log.warn("Retry sync failed for attendant '{}': {}", appUser.getUsername(), ex.getMessage());
        } finally {
            appUserRepository.save(appUser);
        }
    }
}
