package com.couponnumbergenerator.config;

import com.couponnumbergenerator.service.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditInterceptorTest {

    @Mock private SecurityAuditService securityAuditService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private SecurityAuditInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new SecurityAuditInterceptor(securityAuditService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logsAnAllowedMutatingRequest() {
        when(request.getMethod()).thenReturn("POST");
        when(response.getStatus()).thenReturn(201);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(securityAuditService).record(eq(request), any(), eq(201), eq(null));
    }

    @Test
    void doesNotLogAnAllowedGet() {
        when(request.getMethod()).thenReturn("GET");
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(securityAuditService, never()).record(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void logsADeniedGet() {
        when(request.getMethod()).thenReturn("GET");
        when(response.getStatus()).thenReturn(403);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(securityAuditService).record(eq(request), any(), eq(403), eq(null));
    }

    @Test
    void doesNotLogAFailedButNonMutatingNonDeniedRequest() {
        when(request.getMethod()).thenReturn("GET");
        when(response.getStatus()).thenReturn(404);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(securityAuditService, never()).record(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void passesTheHandlerExceptionMessageAsReasonWhenPresent() {
        when(request.getMethod()).thenReturn("PATCH");
        when(response.getStatus()).thenReturn(500);

        interceptor.afterCompletion(request, response, new Object(), new RuntimeException("boom"));

        verify(securityAuditService).record(eq(request), any(), eq(500), eq("boom"));
    }
}
