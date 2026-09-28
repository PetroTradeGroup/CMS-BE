package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.enums.UserSyncStatus;
import com.couponnumbergenerator.model.AppUser;
import com.couponnumbergenerator.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendantSyncSweepTest {

    @Mock private AppUserRepository appUserRepository;
    @Mock private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private AttendantSyncSweep sweep;

    private AppUser pendingAppUser() {
        return AppUser.builder()
                .id(1L)
                .username("jdoe")
                .locationCode("SITE-A")
                .syncStatus(UserSyncStatus.PENDING)
                .syncAttempts(2)
                .build();
    }

    @Test
    void doesNothingWhenNothingIsUnsynced() {
        when(appUserRepository.findBySyncStatusIn(any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        sweep.retryUnsyncedAttendants();

        verify(keycloakAdminClient, never()).syncAttendant(any());
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void marksSyncedAndSavesWhenKeycloakSyncSucceeds() {
        AppUser appUser = pendingAppUser();
        Page<AppUser> page = new PageImpl<>(List.of(appUser));
        when(appUserRepository.findBySyncStatusIn(any(), any(Pageable.class))).thenReturn(page);
        when(keycloakAdminClient.syncAttendant(appUser))
                .thenReturn(new KeycloakAdminClient.SyncResult("kc-9", null));

        sweep.retryUnsyncedAttendants();

        assertThat(appUser.getSyncStatus()).isEqualTo(UserSyncStatus.SYNCED);
        assertThat(appUser.getKeycloakId()).isEqualTo("kc-9");
        verify(appUserRepository).save(appUser);
    }

    @Test
    void marksFailedAndStillSavesWhenKeycloakSyncFailsAgain() {
        AppUser appUser = pendingAppUser();
        Page<AppUser> page = new PageImpl<>(List.of(appUser));
        when(appUserRepository.findBySyncStatusIn(any(), any(Pageable.class))).thenReturn(page);
        when(keycloakAdminClient.syncAttendant(appUser)).thenThrow(new RuntimeException("still down"));

        sweep.retryUnsyncedAttendants();

        assertThat(appUser.getSyncStatus()).isEqualTo(UserSyncStatus.FAILED);
        assertThat(appUser.getSyncAttempts()).isEqualTo(3);
        assertThat(appUser.getLastSyncError()).contains("still down");
        verify(appUserRepository).save(appUser);
    }
}
