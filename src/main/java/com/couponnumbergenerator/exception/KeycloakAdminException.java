package com.couponnumbergenerator.exception;

/** Keycloak's Admin REST API could not be reached or gave back an unexpected response. */
public class KeycloakAdminException extends RuntimeException {

    public KeycloakAdminException(String message) {
        super(message);
    }

    public KeycloakAdminException(String message, Throwable cause) {
        super(message, cause);
    }
}
