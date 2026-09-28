package com.couponnumbergenerator.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/**
 * Records one security-relevant request outcome. Never throws — a failure to persist an audit
 * row must never break the real request it's describing; implementations log and swallow.
 */
public interface SecurityAuditService {

    /**
     * @param authentication null for an unauthenticated request (no valid token presented)
     * @param statusCode     the response's final HTTP status — 401/403 map to
     *                       UNAUTHENTICATED/DENIED, anything else to ALLOWED
     * @param reason         optional detail (e.g. the auth failure message); may be null
     */
    void record(HttpServletRequest request, Authentication authentication, int statusCode, String reason);
}
