package com.couponnumbergenerator.ai;

import com.couponnumbergenerator.enums.CouponStatus;

import java.time.LocalDate;

public record ExtractedFilter(
        LocalDate dateFrom,
        LocalDate dateTo,
        Long fuelTypeId,
        CouponStatus status,
        String interpretation
) {}