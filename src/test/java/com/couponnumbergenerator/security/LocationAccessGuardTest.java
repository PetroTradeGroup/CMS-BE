package com.couponnumbergenerator.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocationAccessGuardTest {

    private final LocationAccessGuard guard = new LocationAccessGuard();

    private JwtAuthenticationToken caller(String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("locationCode", "HRE01").build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)));
    }

    @Test
    void stationStaffAreScopedToTheirLocation() {
        assertThat(guard.callerLocationCode(caller("ROLE_ATTENDANT"))).isEqualTo("HRE01");
    }

    @Test
    void onlyAPlainSalesClerkIsScopedToTheirOwnRequisitions() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("preferred_username", "clerk1").build();
        assertThat(guard.salesClerkOnlyUsername(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_SALES_CLERK"))))).isEqualTo("clerk1");
        assertThat(guard.salesClerkOnlyUsername(new JwtAuthenticationToken(jwt, List.of(
                new SimpleGrantedAuthority("ROLE_SALES_CLERK"), new SimpleGrantedAuthority("ROLE_COMMERCIAL_MANAGER"))))).isNull();
        assertThat(guard.salesClerkOnlyUsername(caller("ROLE_STOCKS_CLERK"))).isNull();
    }

    @Test
    void regionalRepAndCommercialManagerIgnoreAStrayLocationCode() {
        assertThat(guard.callerLocationCode(caller("ROLE_REGIONAL_REP"))).isNull();
        assertThat(guard.callerLocationCode(caller("ROLE_COMMERCIAL_MANAGER"))).isNull();
    }
}
