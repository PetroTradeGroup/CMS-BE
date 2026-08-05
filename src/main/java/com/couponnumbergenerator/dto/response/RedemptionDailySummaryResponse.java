package com.couponnumbergenerator.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What was redeemed on one day: grand totals plus a per-fuel-type breakdown with denomination lines. */
public record RedemptionDailySummaryResponse(
        LocalDate date,
        Long locationId,
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