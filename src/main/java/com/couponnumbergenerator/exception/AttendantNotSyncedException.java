package com.couponnumbergenerator.exception;

/** Thrown when a temporary-password reset is requested for an attendant not yet SYNCED to Keycloak. */
public class AttendantNotSyncedException extends RuntimeException {

    public AttendantNotSyncedException(String username) {
        super("Attendant '%s' is not yet provisioned in Keycloak — wait for it to sync before resetting its password"
                .formatted(username));
    }
}
