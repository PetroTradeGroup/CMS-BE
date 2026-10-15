package com.couponnumbergenerator.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Retail callers (Attendant/Team Leader) carry a {@code locationCode} claim, stamped onto their
 * token at login from their Keycloak user attribute — it identifies which station they're
 * assigned to and can't be altered by the client. Non-retail callers (Stocks/Admin) have no such
 * claim; they aren't tied to one station, so their requests keep asserting location explicitly.
 */
@Component
public class LocationAccessGuard {

    /**
     * A Regional Rep (and the Retail Manager who supervises them) oversees every site, and a Commercial Manager receives requisition stock
     * bound for any site, so any locationCode on their account (e.g. a home station) is ignored —
     * they're treated as non-station, like Stocks/Admin. Otherwise a stray locationCode would
     * narrow their approvals list to their own submissions at one station, i.e. nothing.
     */
    public String callerLocationCode(Authentication authentication) {
        if (hasRole(authentication, "ROLE_REGIONAL_REP") || hasRole(authentication, "ROLE_RETAIL_MANAGER")
                || hasRole(authentication, "ROLE_COMMERCIAL_MANAGER")) {
            return null;
        }
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
        return hasRole(authentication, "ROLE_TEAM_LEADER");
    }

    /**
     * A Sales Clerk with no broader role only ever sees their own requisitions — and the issues
     * (transfers) raised against them, which they receive. Returns their username to scope by, or
     * null when the caller has any broader role (Stocks, Commercial, Admin, …) and sees everything.
     */
    public String salesClerkOnlyUsername(Authentication authentication) {
        boolean clerkOnly = hasRole(authentication, "ROLE_SALES_CLERK")
                && BROADER_THAN_SALES_CLERK.stream().noneMatch(role -> hasRole(authentication, role));
        return clerkOnly ? callerUsername(authentication) : null;
    }

    private static final Set<String> BROADER_THAN_SALES_CLERK = Set.of("ROLE_ADMIN", "ROLE_STOCKS_CLERK",
            "ROLE_STOCKS_CONTROLLER", "ROLE_REGIONAL_REP", "ROLE_RETAIL_MANAGER", "ROLE_ACCOUNTS_CLERK", "ROLE_COMMERCIAL_MANAGER");

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
