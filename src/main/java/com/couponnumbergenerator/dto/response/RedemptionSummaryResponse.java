package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What was redeemed from dateFrom to dateTo inclusive: grand totals plus a per-fuel-type breakdown
 * with denomination lines, and a per-site breakdown. {@code attendantUsername} is null for a site/location-level summary and
 * set only when narrowed to one attendant's own redemptions (see {@code GET /summary/by-attendant/{username}}).
 * {@code companyName} is the company filtered on, or the company of the one site the report is scoped to;
 * null when the report spans every company.
 */
public record RedemptionSummaryResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        Long locationId,
        Long fuelTypeId,
        Long companyId,
        String companyName,
        String attendantUsername,
        /** PHYSICAL or DIGITAL when filtered to one coupon type; null = both. */
        CouponType couponType,
        long totalCoupons,
        BigDecimal totalLitres,
        List<FuelTypeSummary> byFuelType,
        List<SiteLine> bySite
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

    /** One site's share of the total, with the company it belongs to. */
    public record SiteLine(Long locationId, String locationCode, String locationName, String companyName,
                           long count, BigDecimal litres) {}
}
