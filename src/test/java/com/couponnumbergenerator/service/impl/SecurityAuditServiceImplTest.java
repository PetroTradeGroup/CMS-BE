package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.enums.SecurityAuditOutcome;
import com.couponnumbergenerator.model.SecurityAuditEvent;
import com.couponnumbergenerator.repository.SecurityAuditEventRepository;
import com.couponnumbergenerator.security.LocationAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceImplTest {

    @Mock private SecurityAuditEventRepository securityAuditEventRepository;
    @Mock private HttpServletRequest request;

    private final LocationAccessGuard locationAccessGuard = new LocationAccessGuard();

    private SecurityAuditServiceImpl service;

    private Jwt jwtFor(String username) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("preferred_username", username)
                .build();
    }

    @Test
    void recordsAllowedMutationWithPrincipalAndRoles() {
        service = new SecurityAuditServiceImpl(securityAuditEventRepository, locationAccessGuard);
        Authentication authentication = new TestingAuthenticationToken(
                jwtFor("clerk1"), null, List.of(new SimpleGrantedAuthority("ROLE_STOCKS_CLERK")));
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/coupons/generate");

        service.record(request, authentication, 201, null);

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(securityAuditEventRepository).save(captor.capture());
        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getPrincipal()).isEqualTo("clerk1");
        assertThat(saved.getRoles()).isEqualTo("ROLE_STOCKS_CLERK");
        assertThat(saved.getHttpMethod()).isEqualTo("POST");
        assertThat(saved.getPath()).isEqualTo("/api/v1/coupons/generate");
        assertThat(saved.getStatusCode()).isEqualTo(201);
        assertThat(saved.getOutcome()).isEqualTo(SecurityAuditOutcome.ALLOWED);
    }

    @Test
    void recordsUnauthenticatedWithNullAuthenticationAndNoPrincipal() {
        service = new SecurityAuditServiceImpl(securityAuditEventRepository, locationAccessGuard);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/coupons");

        service.record(request, null, 401, "JWT expired");

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(securityAuditEventRepository).save(captor.capture());
        SecurityAuditEvent saved = captor.getValue();
        assertThat(saved.getPrincipal()).isNull();
        assertThat(saved.getRoles()).isNull();
        assertThat(saved.getOutcome()).isEqualTo(SecurityAuditOutcome.UNAUTHENTICATED);
        assertThat(saved.getReason()).isEqualTo("JWT expired");
    }

    @Test
    void mapsForbiddenStatusToDenied() {
        service = new SecurityAuditServiceImpl(securityAuditEventRepository, locationAccessGuard);
        Authentication authentication = new TestingAuthenticationToken(
                jwtFor("attendant1"), null, List.of(new SimpleGrantedAuthority("ROLE_ATTENDANT")));
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/api/v1/coupons/1");

        service.record(request, authentication, 403, null);

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(securityAuditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(SecurityAuditOutcome.DENIED);
    }

    @Test
    void aRepositoryFailureIsSwallowedNotPropagated() {
        service = new SecurityAuditServiceImpl(securityAuditEventRepository, locationAccessGuard);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/coupons/generate");
        doThrow(new RuntimeException("db down")).when(securityAuditEventRepository).save(any());

        service.record(request, null, 201, null);
        // No exception propagated — audit logging must never break the request it's describing.
    }

    @Test
    void aNonJwtPrincipalYieldsNullPrincipalRatherThanFailing() {
        service = new SecurityAuditServiceImpl(securityAuditEventRepository, locationAccessGuard);
        Authentication authentication = new TestingAuthenticationToken("not-a-jwt", null);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/coupons/generate");

        service.record(request, authentication, 201, null);

        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(securityAuditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isNull();
    }
}
