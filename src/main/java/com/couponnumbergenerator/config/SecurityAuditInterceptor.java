package com.couponnumbergenerator.config;

import com.couponnumbergenerator.service.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Runs from inside {@code DispatcherServlet}'s dispatch — after {@code @PreAuthorize} and any
 * {@code GlobalExceptionHandler} translation have already resolved the response's final status,
 * but before Spring Security clears {@link SecurityContextHolder} for the request. That ordering
 * is exactly why this is a {@link HandlerInterceptor} and not a {@code Filter} wrapping the
 * whole chain: a filter positioned around Spring Security would see an empty security context by
 * the time {@code doFilter} returns control to it.
 *
 * <p>Deliberately does not log a plain, allowed GET — high volume, no audit value. Logs every
 * mutating request that was allowed, and every request of any method that was denied (403) or
 * unauthenticated (401) — the latter case in practice never reaches here (it's rejected before
 * dispatch even starts; see {@code SecurityConfig}'s authenticationEntryPoint), kept as a
 * defensive fallback only.
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final SecurityAuditService securityAuditService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        int status = response.getStatus();
        boolean mutating = MUTATING_METHODS.contains(request.getMethod());
        boolean denied = status == HttpServletResponse.SC_FORBIDDEN || status == HttpServletResponse.SC_UNAUTHORIZED;
        if (!mutating && !denied) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        securityAuditService.record(request, authentication, status, ex == null ? null : ex.getMessage());
    }
}
