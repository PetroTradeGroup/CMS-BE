package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;

import java.time.LocalDate;

public record CouponFilterRequest(
        LocalDate dateFrom,
        LocalDate dateTo,
        Long fuelTypeId,
        CouponStatus status,
        Long locationId,
        CouponType couponType,
        Long batchId,
        Long departmentId,
        String batchNumber
) {
    public CouponFilterRequest(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId, CouponStatus status) {
        this(dateFrom, dateTo, fuelTypeId, status, null, null, null, null, null);
    }

    public CouponFilterRequest(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId, CouponStatus status,
                                Long locationId, CouponType couponType, Long batchId) {
        this(dateFrom, dateTo, fuelTypeId, status, locationId, couponType, batchId, null, null);
    }

    public CouponFilterRequest(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId, CouponStatus status,
                                Long locationId, CouponType couponType, Long batchId, Long departmentId) {
        this(dateFrom, dateTo, fuelTypeId, status, locationId, couponType, batchId, departmentId, null);
    }

    public static CouponFilterRequest empty() {
        return new CouponFilterRequest(null, null, null, null, null, null, null, null, null);
    }
}
