package com.couponnumbergenerator.enums;

/** The result of one request as far as authentication/authorization is concerned. */
public enum SecurityAuditOutcome {
    /** Authenticated and authorized — request reached its handler and completed. */
    ALLOWED,
    /** Authenticated, but rejected by role/department authorization (403). */
    DENIED,
    /** No valid credentials presented at all (401) — never reached a controller. */
    UNAUTHENTICATED
}
