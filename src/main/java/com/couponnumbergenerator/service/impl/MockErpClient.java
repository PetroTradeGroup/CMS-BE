package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.ErpRedemptionRequest;
import com.couponnumbergenerator.service.ErpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Stand-in for the real ERP while its endpoint doesn't exist yet ({@code app.erp.mock=true}):
 * "posts" every redemption successfully with a locally generated document number, so the
 * auto-post flow can be exercised end-to-end. Swap to {@link RestErpClient} by setting
 * {@code app.erp.mock=false} once the real endpoint and API key are available.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.erp", name = "mock", havingValue = "true")
public class MockErpClient implements ErpClient {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public String postRedemption(ErpRedemptionRequest request) {
        String documentNumber = "MOCK-%d-%s".formatted(request.referenceId(), LocalDateTime.now().format(TIMESTAMP));
        log.info("MOCK ERP: redemption request {} ({} coupon(s), {} litres) posted as document {}",
                request.referenceId(), request.couponCount(), request.totalValue(), documentNumber);
        return documentNumber;
    }
}