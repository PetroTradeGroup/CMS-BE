package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponOrigin;
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
        String batchNumber,
        /** Partial, case-insensitive match on the coupon number — the frontend's search box. */
        String couponNumber,
        /** GENERATED or LEGACY_IMPORT — e.g. to see all legacy-imported coupons in one view. */
        CouponOrigin origin
) {
    public CouponFilterRequest(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId, CouponStatus status) {
        this(dateFrom, dateTo, fuelTypeId, status, null, null, null, null, null, null, null);
    }

    public CouponFilterRequest(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId, CouponStatus status,
                                Long locationId, CouponType couponType, Long batchId) {
        this(dateFrom, dateTo, fuelTypeId, status, locationId, couponType, batchId, null, null, null, null);
    }

    public CouponFilterRequest(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId, CouponStatus status,
                                Long locationId, CouponType couponType, Long batchId, Long departmentId) {
        this(dateFrom, dateTo, fuelTypeId, status, locationId, couponType, batchId, departmentId, null, null, null);
    }

    public CouponFilterRequest(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId, CouponStatus status,
                                Long locationId, CouponType couponType, Long batchId, Long departmentId,
                                String batchNumber) {
        this(dateFrom, dateTo, fuelTypeId, status, locationId, couponType, batchId, departmentId, batchNumber,
                null, null);
    }

    public CouponFilterRequest(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId, CouponStatus status,
                                Long locationId, CouponType couponType, Long batchId, Long departmentId,
                                String batchNumber, String couponNumber) {
        this(dateFrom, dateTo, fuelTypeId, status, locationId, couponType, batchId, departmentId, batchNumber,
                couponNumber, null);
    }

    public static CouponFilterRequest empty() {
        return new CouponFilterRequest(null, null, null, null, null, null, null, null, null, null, null);
    }
}
