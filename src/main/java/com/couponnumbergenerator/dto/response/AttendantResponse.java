package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.UserSyncStatus;
import com.couponnumbergenerator.model.AppUser;

public record AttendantResponse(
        Long id,
        String keycloakUserId,
        String username,
        String email,
        String locationCode,
        UserSyncStatus syncStatus,
        String temporaryPassword
) {

    public static AttendantResponse synced(AppUser appUser, String temporaryPassword) {
        return new AttendantResponse(appUser.getId(), appUser.getKeycloakId(), appUser.getUsername(),
                appUser.getEmail(), appUser.getLocationCode(), UserSyncStatus.SYNCED, temporaryPassword);
    }

    public static AttendantResponse pending(AppUser appUser) {
        return new AttendantResponse(appUser.getId(), appUser.getKeycloakId(), appUser.getUsername(),
                appUser.getEmail(), appUser.getLocationCode(), appUser.getSyncStatus(), null);
    }
}
