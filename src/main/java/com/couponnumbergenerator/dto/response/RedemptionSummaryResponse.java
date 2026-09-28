package com.couponnumbergenerator.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What was redeemed from dateFrom to dateTo inclusive: grand totals plus a per-fuel-type breakdown
 * with denomination lines. {@code attendantUsername} is null for a site/location-level summary and
 * set only when narrowed to one attendant's own redemptions (see {@code GET /summary/by-attendant/{username}}).
 */
public record RedemptionSummaryResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        Long locationId,
        Long fuelTypeId,
        String attendantUsername,
        long totalCoupons,
        BigDecimal totalLitres,
        List<FuelTypeSummary> byFuelType
) {

    public record FuelTypeSummary(
            Long fuelTypeId,
            String fuelTypeName,
            long count,
            BigDecimal litres,
            List<DenominationLine> byDenomination
    ) {}

    /** {@code count} coupons of one denomination, worth {@code litres} in total. */
    public record DenominationLine(BigDecimal denomination, long count, BigDecimal litres) {}
}
