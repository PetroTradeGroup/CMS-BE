package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.enums.SecurityAuditOutcome;
import com.couponnumbergenerator.model.SecurityAuditEvent;
import com.couponnumbergenerator.repository.SecurityAuditEventRepository;
import com.couponnumbergenerator.security.LocationAccessGuard;
import com.couponnumbergenerator.service.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditServiceImpl implements SecurityAuditService {

    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final LocationAccessGuard locationAccessGuard;

    @Override
    public void record(HttpServletRequest request, Authentication authentication, int statusCode, String reason) {
        try {
            securityAuditEventRepository.save(SecurityAuditEvent.builder()
                    .principal(extractPrincipal(authentication))
                    .roles(extractRoles(authentication))
                    .httpMethod(request.getMethod())
                    .path(request.getRequestURI())
                    .statusCode(statusCode)
                    .outcome(outcomeFor(statusCode))
                    .reason(reason)
                    .build());
        } catch (Exception ex) {
            // Audit logging is best-effort — it must never take down the request it's describing.
            log.warn("Failed to record security audit event for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
    }

    private String extractPrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return null;
        }
        return locationAccessGuard.callerUsername(authentication);
    }

    private String extractRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().isEmpty()) {
            return null;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }

    private SecurityAuditOutcome outcomeFor(int statusCode) {
        if (statusCode == HttpStatus.UNAUTHORIZED.value()) {
            return SecurityAuditOutcome.UNAUTHENTICATED;
        }
        if (statusCode == HttpStatus.FORBIDDEN.value()) {
            return SecurityAuditOutcome.DENIED;
        }
        return SecurityAuditOutcome.ALLOWED;
    }
}
