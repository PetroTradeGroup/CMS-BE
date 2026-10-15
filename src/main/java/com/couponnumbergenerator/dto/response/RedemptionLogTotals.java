package com.couponnumbergenerator.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Totals for GET /redemptions/log's filters across the whole range; REJECTED redemptions excluded. */
public record RedemptionLogTotals(
        LocalDate dateFrom,
        LocalDate dateTo,
        long redemptions,
        long coupons,
        BigDecimal litres
) {
}
