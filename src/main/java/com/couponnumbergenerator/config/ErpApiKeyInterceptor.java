package com.couponnumbergenerator.config;

import com.couponnumbergenerator.exception.InvalidApiKeyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** No check at all while no key is configured (local/mock dev) — mirrors the outbound ERP client's convention. */
@Component
@RequiredArgsConstructor
public class ErpApiKeyInterceptor implements HandlerInterceptor {

    private final ErpSalesProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String expectedKey = properties.inboundApiKey();
        if (expectedKey.isBlank()) {
            return true;
        }
        String suppliedKey = request.getHeader("X-API-Key");
        if (!expectedKey.equals(suppliedKey)) {
            throw new InvalidApiKeyException("Missing or invalid X-API-Key header");
        }
        return true;
    }
}