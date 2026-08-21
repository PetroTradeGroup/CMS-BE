package com.couponnumbergenerator.security;

import com.couponnumbergenerator.exception.DepartmentAccessDeniedException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepartmentAccessGuardTest {

    private final DepartmentAccessGuard guard = new DepartmentAccessGuard();

    private Authentication authenticationWithDepartment(String department) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("department", department)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getPrincipal()).thenReturn(jwt);
        return authentication;
    }

    @Test
    void allowsWhenTheCallersDepartmentMatches() {
        guard.assertDepartment("STOCKS", authenticationWithDepartment("STOCKS"));
    }

    @Test
    void deniesWhenTheCallersDepartmentDoesNotMatch() {
        assertThatThrownBy(() -> guard.assertDepartment("STOCKS", authenticationWithDepartment("COMMERCIAL")))
                .isInstanceOf(DepartmentAccessDeniedException.class)
                .hasMessageContaining("COMMERCIAL")
                .hasMessageContaining("STOCKS");
    }

    @Test
    void deniesWhenTheJwtHasNoDepartmentClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-without-department")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getPrincipal()).thenReturn(jwt);

        assertThatThrownBy(() -> guard.assertDepartment("STOCKS", authentication))
                .isInstanceOf(DepartmentAccessDeniedException.class);
    }
}
