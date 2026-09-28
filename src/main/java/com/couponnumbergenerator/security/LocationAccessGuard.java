package com.couponnumbergenerator.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Retail callers (Attendant/Team Leader) carry a {@code locationCode} claim, stamped onto their
 * token at login from their Keycloak user attribute — it identifies which station they're
 * assigned to and can't be altered by the client. Non-retail callers (Stocks/Admin) have no such
 * claim; they aren't tied to one station, so their requests keep asserting location explicitly.
 */
@Component
public class LocationAccessGuard {

    public String callerLocationCode(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaimAsString("locationCode");
    }

    public String callerUsername(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getClaimAsString("preferred_username");
    }

    /**
     * Team Leader has station-level oversight (per the role description) — sees every request at
     * their site. A plain Attendant only sees their own; callers scope further by
     * {@link #callerUsername} when this returns false.
     */
    public boolean callerIsTeamLeader(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_TEAM_LEADER"));
    }
}
