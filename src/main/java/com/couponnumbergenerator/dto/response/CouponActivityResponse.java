package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.FuelTypeSummary;
import com.couponnumbergenerator.enums.CouponStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Coupons that moved into each status from dateFrom to dateTo inclusive — per status a total, then the
 * same fuel type → denomination breakdown as the redemption summary. No grand total across statuses:
 * litres generated plus litres redeemed isn't a meaningful number.
 */
public record CouponActivityResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        CouponStatus status,
        Long locationId,
        Long fuelTypeId,
        List<StatusSummary> byStatus
) {

    public record StatusSummary(CouponStatus status, long count, BigDecimal litres, List<FuelTypeSummary> byFuelType) {}
}
