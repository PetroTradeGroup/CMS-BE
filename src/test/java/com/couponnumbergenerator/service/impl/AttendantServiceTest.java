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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendantServiceTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private LocationAccessGuard locationAccessGuard;
    @Mock private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private AttendantService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private CreateAttendantRequest request(String username, String locationCode) {
        return new CreateAttendantRequest(username, username + "@example.com", "Jane", "Doe", locationCode);
    }

    /** Simulates JPA identity generation: assigns an id on first save, keeps it on later saves. */
    private void stubSaveAssignsId() {
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser appUser = invocation.getArgument(0);
            if (appUser.getId() == null) {
                appUser.setId(1L);
            }
            return appUser;
        });
    }

    @Test
    void registerAttendantIsSavedLocallyAsPendingBeforeKeycloakIsEverCalled() {
        when(locationRepository.existsByCodeIgnoreCase("SITE-A")).thenReturn(true);
        when(appUserRepository.existsByUsernameIgnoreCase("jdoe")).thenReturn(false);

        List<UserSyncStatus> statusesAtSaveTime = new ArrayList<>();
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser appUser = invocation.getArgument(0);
            statusesAtSaveTime.add(appUser.getSyncStatus());
            if (appUser.getId() == null) {
                appUser.setId(1L);
            }
            return appUser;
        });
        when(keycloakAdminClient.syncAttendant(any(AppUser.class)))
                .thenReturn(new KeycloakAdminClient.SyncResult("kc-1", "TempPass123"));

        AttendantResponse response = service.registerAttendant(request("jdoe", "SITE-A"));

        // Saved PENDING first (durable even if the Keycloak call below never happened), then SYNCED.
        assertThat(statusesAtSaveTime).containsExactly(UserSyncStatus.PENDING, UserSyncStatus.SYNCED);
        assertThat(response.syncStatus()).isEqualTo(UserSyncStatus.SYNCED);
        assertThat(response.keycloakUserId()).isEqualTo("kc-1");
        assertThat(response.temporaryPassword()).isEqualTo("TempPass123");
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void registerAttendantThrowsWhenLocationDoesNotExistAndNeverTouchesLocalOrKeycloakState() {
        when(locationRepository.existsByCodeIgnoreCase("NOPE")).thenReturn(false);

        assertThatThrownBy(() -> service.registerAttendant(request("jdoe", "NOPE")))
                .isInstanceOf(LocationNotFoundException.class);

        verify(appUserRepository, never()).save(any());
        verify(keycloakAdminClient, never()).syncAttendant(any());
    }

    @Test
    void registerAttendantThrowsWhenUsernameAlreadyTakenLocally() {
        when(locationRepository.existsByCodeIgnoreCase("SITE-A")).thenReturn(true);
        when(appUserRepository.existsByUsernameIgnoreCase("jdoe")).thenReturn(true);

        assertThatThrownBy(() -> service.registerAttendant(request("jdoe", "SITE-A")))
                .isInstanceOf(AttendantAlreadyExistsException.class);

        verify(appUserRepository, never()).save(any());
        verify(keycloakAdminClient, never()).syncAttendant(any());
    }

    @Test
    void registerAttendantLeavesRowQueuedWhenKeycloakIsUnreachable() {
        when(locationRepository.existsByCodeIgnoreCase("SITE-A")).thenReturn(true);
        when(appUserRepository.existsByUsernameIgnoreCase("jdoe")).thenReturn(false);
        stubSaveAssignsId();
        when(keycloakAdminClient.syncAttendant(any(AppUser.class)))
                .thenThrow(new KeycloakAdminException("Keycloak is down"));

        AttendantResponse response = service.registerAttendant(request("jdoe", "SITE-A"));

        assertThat(response.syncStatus()).isEqualTo(UserSyncStatus.FAILED);
        assertThat(response.keycloakUserId()).isNull();
        assertThat(response.temporaryPassword()).isNull();
        assertThat(response.id()).isEqualTo(1L);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository, times(2)).save(captor.capture());
        AppUser saved = captor.getValue();
        assertThat(saved.getSyncAttempts()).isEqualTo(1);
        assertThat(saved.getLastSyncError()).contains("Keycloak is down");
    }

    @Test
    void registerAttendantDefaultsLocationCodeFromCallerWhenOmitted() {
        Authentication authentication = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(locationAccessGuard.callerLocationCode(authentication)).thenReturn("SITE-B");
        when(locationRepository.existsByCodeIgnoreCase("SITE-B")).thenReturn(true);
        when(appUserRepository.existsByUsernameIgnoreCase("jdoe")).thenReturn(false);
        stubSaveAssignsId();
        when(keycloakAdminClient.syncAttendant(any(AppUser.class)))
                .thenReturn(new KeycloakAdminClient.SyncResult("kc-1", "pw"));

        AttendantResponse response = service.registerAttendant(request("jdoe", null));

        assertThat(response.locationCode()).isEqualTo("SITE-B");
    }

    @Test
    void registerAttendantRejectsMissingLocationCodeWhenCallerHasNoStationOfTheirOwn() {
        Authentication authentication = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(locationAccessGuard.callerLocationCode(authentication)).thenReturn(null);

        assertThatThrownBy(() -> service.registerAttendant(request("jdoe", null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void resetTemporaryPasswordThrowsWhenAttendantUnknown() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetTemporaryPassword(99L))
                .isInstanceOf(AttendantNotFoundException.class);
    }

    @Test
    void resetTemporaryPasswordThrowsWhenAttendantNotYetSynced() {
        AppUser appUser = AppUser.builder()
                .id(1L).username("jdoe").syncStatus(UserSyncStatus.PENDING).build();
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(appUser));

        assertThatThrownBy(() -> service.resetTemporaryPassword(1L))
                .isInstanceOf(AttendantNotSyncedException.class);

        verify(keycloakAdminClient, never()).resetTemporaryPassword(any());
    }

    @Test
    void resetTemporaryPasswordIssuesFreshPasswordForSyncedAttendant() {
        AppUser appUser = AppUser.builder()
                .id(1L).username("jdoe").keycloakId("kc-1").syncStatus(UserSyncStatus.SYNCED).build();
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(appUser));
        when(keycloakAdminClient.resetTemporaryPassword("kc-1")).thenReturn("NewPass456");

        AttendantResponse response = service.resetTemporaryPassword(1L);

        assertThat(response.temporaryPassword()).isEqualTo("NewPass456");
        assertThat(response.syncStatus()).isEqualTo(UserSyncStatus.SYNCED);
    }
}
