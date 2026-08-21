package com.couponnumbergenerator.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for a real bug caught only by hitting a live Keycloak instance: Spring
 * Security's default JWT-to-authorities conversion reads the "scope"/"scp" claim, but Keycloak
 * puts realm roles in the nested "realm_access.roles" claim instead — without a custom converter,
 * every @PreAuthorize(hasRole(...)) check 403s unconditionally, regardless of the caller's actual
 * roles. A MockMvc jwt() postprocessor test with .authorities(...) can't catch this, since it
 * bypasses real conversion entirely — hence this direct, Spring-context-free unit test.
 */
class SecurityConfigTest {

    private final SecurityConfig securityConfig =
            new SecurityConfig("http://localhost:8189/realms/petrotrade/protocol/openid-connect/certs",
                    "http://localhost:8189/realms/petrotrade");

    private Jwt jwtWithClaims(Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        extraClaims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void extractsKeycloakRealmRolesAsRoleAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("STOCKS_CLERK", "ADMIN"))));

        assertThat(securityConfig.realmRoleAuthorities(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_STOCKS_CLERK", "ROLE_ADMIN");
    }

    @Test
    void noRealmAccessClaimYieldsNoAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("sub", "user-without-roles"));

        assertThat(securityConfig.realmRoleAuthorities(jwt)).isEmpty();
    }

    @Test
    void realmAccessWithoutRolesYieldsNoAuthorities() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of()));

        assertThat(securityConfig.realmRoleAuthorities(jwt)).isEmpty();
    }
}
