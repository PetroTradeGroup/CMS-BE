package com.couponnumbergenerator.config;

import com.couponnumbergenerator.exception.InvalidApiKeyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * AD-5: fails closed — an unset/blank key rejects every request rather than skipping the check.
 * Registered (WebConfig) against the whole {@code /erp/sales/**} path, but the X-API-Key check
 * only applies to POST (the inbound webhook) — GET (the human-facing sales log) is JWT +
 * {@code @PreAuthorize}-gated instead (SecurityConfig), not API-key-gated.
 */
@Component
@RequiredArgsConstructor
public class ErpApiKeyInterceptor implements HandlerInterceptor {

    private final ErpSalesProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String expectedKey = properties.inboundApiKey();
        String suppliedKey = request.getHeader("X-API-Key");
        if (expectedKey.isBlank() || !expectedKey.equals(suppliedKey)) {
            throw new InvalidApiKeyException("Missing or invalid X-API-Key header");
        }
        return true;
    }
}