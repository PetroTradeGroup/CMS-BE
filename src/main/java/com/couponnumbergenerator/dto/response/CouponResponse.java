package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.model.Coupon;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CouponResponse(
        Long id,
        String couponNumber,
        FuelTypeResponse fuelType,
        CouponStatus status,
        BigDecimal denomination,
        CouponType couponType,
        LocalDate expiryDate,
        LocationResponse location,
        DepartmentResponse department,
        String batchNumber,
        Integer batchSequence,
        LocalDateTime createdAt
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCouponNumber(),
                FuelTypeResponse.from(coupon.getFuelType()),
                coupon.getStatus(),
                coupon.getDenomination(),
                coupon.getCouponType(),
                coupon.getExpiryDate(),
                LocationResponse.from(coupon.getCurrentLocation()),
                DepartmentResponse.from(coupon.getCurrentDepartment()),
                coupon.getBatch() == null ? null : coupon.getBatch().getBatchNumber(),
                coupon.getBatchSequence(),
                coupon.getCreatedAt()
        );
    }
}
