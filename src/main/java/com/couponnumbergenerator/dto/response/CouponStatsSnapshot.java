package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CouponStatsSnapshot(
        long totalCoupons,
        Map<CouponStatus, Long> byStatus,
        long issuedThisWeek,
        long issuedLastWeek,
        List<FuelTypeStats> fuelTypeBreakdown,
        List<SequenceCapacity> sequenceCapacities,
        LocalDate asOf
) {

    public record FuelTypeStats(
            String fuelTypeName,
            String typeCode,
            long totalIssued,
            Map<CouponStatus, Long> byStatus
    ) {}

    public record SequenceCapacity(
            String fuelTypeName,
            String currentLetter,
            long issuedInCurrentLetter,
            double capacityUsedPercent
    ) {}
}