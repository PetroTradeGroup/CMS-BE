package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponType;

import java.time.LocalDate;

public record BatchFilterRequest(
        Long fuelTypeId,
        CouponType couponType,
        Long locationId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Boolean hasStock,
        /** Partial, case-insensitive match on the batch number — the batch list search box. */
        String batchNumber
) {
    public BatchFilterRequest(Long fuelTypeId, CouponType couponType, Long locationId,
                              LocalDate dateFrom, LocalDate dateTo, Boolean hasStock) {
        this(fuelTypeId, couponType, locationId, dateFrom, dateTo, hasStock, null);
    }
}
