package com.couponnumbergenerator.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The payload posted to the ERP's redemption endpoint. {@code referenceId} is our approval
 * request ID — the ERP should treat it as an idempotency key, since a redemption may be
 * re-posted if an earlier attempt failed mid-flight.
 */
public record ErpRedemptionRequest(
        Long referenceId,
        String locationCode,
        String locationName,
        int couponCount,
        BigDecimal totalValue,
        List<String> couponNumbers,
        List<Line> denominations,
        String submittedBy,
        LocalDateTime submittedAt
) {

    /** One denomination line of the redemption: {@code count} coupons worth {@code subtotal}. */
    public record Line(BigDecimal denomination, int count, BigDecimal subtotal) {}
}