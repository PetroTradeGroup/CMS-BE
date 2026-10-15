package com.couponnumbergenerator.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Who redeemed what, from dateFrom to dateTo inclusive, at one site (or across all sites). */
public record RedemptionByAttendantResponse(
        LocalDate dateFrom,
        LocalDate dateTo,
        Long locationId,
        Long fuelTypeId,
        long totalCoupons,
        BigDecimal totalLitres,
        List<AttendantLine> byAttendant
) {

    /** attendantName is "First Last" from the attendant register, or null for an account registered outside this app. */
    public record AttendantLine(String requestedBy, String attendantName, long count, BigDecimal litres) {}
}
