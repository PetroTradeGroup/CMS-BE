package com.couponnumbergenerator.config;

import com.couponnumbergenerator.exception.InvalidApiKeyException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for a real bug caught only by hitting the live app: the interceptor is
 * registered against the whole /erp/sales/** path (WebConfig), so without a method check here, the
 * AD-5 fail-closed fix also blocked the human-facing GET sales log for every JWT-authenticated
 * user, not just the unkeyed POST webhook it was meant to close the gap on.
 */
class ErpApiKeyInterceptorTest {

    private final ErpSalesProperties properties = new ErpSalesProperties(
            true, false, "http://localhost:9091", "/api/coupon-sales/confirm", "the-real-key", 30, 300000);
    private final ErpApiKeyInterceptor interceptor = new ErpApiKeyInterceptor(properties);

    private HttpServletRequest request(String method, String suppliedKey) {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getMethod()).thenReturn(method);
        Mockito.when(request.getHeader("X-API-Key")).thenReturn(suppliedKey);
        return request;
    }

    @Test
    void getRequestsBypassTheKeyCheckEntirely() {
        boolean result = interceptor.preHandle(request("GET", null), Mockito.mock(HttpServletResponse.class), new Object());
        assertThat(result).isTrue();
    }

    @Test
    void postWithTheCorrectKeySucceeds() {
        boolean result = interceptor.preHandle(request("POST", "the-real-key"), Mockito.mock(HttpServletResponse.class), new Object());
        assertThat(result).isTrue();
    }

    @Test
    void postWithAMissingKeyFailsClosed() {
        assertThatThrownBy(() -> interceptor.preHandle(request("POST", null), Mockito.mock(HttpServletResponse.class), new Object()))
                .isInstanceOf(InvalidApiKeyException.class);
    }

    @Test
    void postWithAnUnsetInboundKeyFailsClosed() {
        ErpSalesProperties blankKeyProperties = new ErpSalesProperties(
                true, false, "http://localhost:9091", "/api/coupon-sales/confirm", "", 30, 300000);
        ErpApiKeyInterceptor blankKeyInterceptor = new ErpApiKeyInterceptor(blankKeyProperties);

        assertThatThrownBy(() -> blankKeyInterceptor.preHandle(request("POST", "anything"), Mockito.mock(HttpServletResponse.class), new Object()))
                .isInstanceOf(InvalidApiKeyException.class);
    }
}
