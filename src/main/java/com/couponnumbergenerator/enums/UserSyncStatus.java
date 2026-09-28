package com.couponnumbergenerator.enums;

/** Where an {@link com.couponnumbergenerator.model.AppUser} stands against Keycloak. */
public enum UserSyncStatus {
    /** Saved locally, not yet attempted or awaiting the next retry sweep. */
    PENDING,
    /** Created (or found already existing) in Keycloak and joined to /RETAIL. */
    SYNCED,
    /** At least one sync attempt failed — see lastSyncError; still retried by the sweep. */
    FAILED
}
