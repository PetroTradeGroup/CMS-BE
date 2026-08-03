package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponType;

import java.time.LocalDate;

public record BatchFilterRequest(
        Long fuelTypeId,
        CouponType couponType,
        Long locationId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Boolean hasStock
) {}