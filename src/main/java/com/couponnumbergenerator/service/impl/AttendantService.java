package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.CreateAttendantRequest;
import com.couponnumbergenerator.dto.response.AttendantResponse;
import com.couponnumbergenerator.enums.UserSyncStatus;
import com.couponnumbergenerator.exception.AttendantAlreadyExistsException;
import com.couponnumbergenerator.exception.AttendantNotFoundException;
import com.couponnumbergenerator.exception.AttendantNotSyncedException;
import com.couponnumbergenerator.exception.KeycloakAdminException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.AppUser;
import com.couponnumbergenerator.repository.AppUserRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.security.LocationAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Registers attendants durably: the {@link AppUser} row is saved as PENDING before Keycloak is
 * ever called, so a registration survives a Keycloak outage instead of being lost. The Keycloak
 * sync is then attempted immediately for the common case (Keycloak up), and left for
 * {@link AttendantSyncSweep} to retry if it fails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendantService {

    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final LocationAccessGuard locationAccessGuard;
    private final KeycloakAdminClient keycloakAdminClient;

    public AttendantResponse registerAttendant(CreateAttendantRequest request) {
        String locationCode = resolveLocationCode(request.locationCode());
        if (!locationRepository.existsByCodeIgnoreCase(locationCode)) {
            throw new LocationNotFoundException("No station with locationCode '%s'".formatted(locationCode));
        }
        if (appUserRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new AttendantAlreadyExistsException(request.username());
        }

        AppUser appUser = appUserRepository.save(AppUser.builder()
                .username(request.username())
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .locationCode(locationCode)
                .syncStatus(UserSyncStatus.PENDING)
                .build());

        return attemptSync(appUser);
    }

    public AttendantResponse resetTemporaryPassword(Long appUserId) {
        AppUser appUser = appUserRepository.findById(appUserId)
                .orElseThrow(() -> new AttendantNotFoundException(appUserId));
        if (appUser.getSyncStatus() != UserSyncStatus.SYNCED || appUser.getKeycloakId() == null) {
            throw new AttendantNotSyncedException(appUser.getUsername());
        }
        String temporaryPassword = keycloakAdminClient.resetTemporaryPassword(appUser.getKeycloakId());
        return AttendantResponse.synced(appUser, temporaryPassword);
    }

    /** Best-effort immediate sync right after registration; on failure the row stays PENDING for the sweep. */
    private AttendantResponse attemptSync(AppUser appUser) {
        try {
            KeycloakAdminClient.SyncResult result = keycloakAdminClient.syncAttendant(appUser);
            appUser.markSynced(result.keycloakId());
            appUserRepository.save(appUser);
            return AttendantResponse.synced(appUser, result.temporaryPassword());
        } catch (KeycloakAdminException ex) {
            appUser.markFailed(ex.getMessage());
            appUserRepository.save(appUser);
            log.warn("Keycloak sync failed for attendant '{}' — left {} for retry: {}",
                    appUser.getUsername(), appUser.getSyncStatus(), ex.getMessage());
            return AttendantResponse.pending(appUser);
        }
    }

    /**
     * Defaults to the caller's own station (their token's {@code locationCode} claim) so a Team
     * Leader registering someone for their own site never has to pick from a list. An explicit
     * value in the request always wins, so a Team Leader can still register staff elsewhere. A
     * caller with no station of their own (e.g. Admin) must supply one.
     */
    private String resolveLocationCode(String requestedLocationCode) {
        if (requestedLocationCode != null && !requestedLocationCode.isBlank()) {
            return requestedLocationCode;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String callerLocationCode = locationAccessGuard.callerLocationCode(authentication);
        if (callerLocationCode == null) {
            throw new IllegalArgumentException(
                    "locationCode is required — you have no station of your own to default to");
        }
        return callerLocationCode;
    }
}
