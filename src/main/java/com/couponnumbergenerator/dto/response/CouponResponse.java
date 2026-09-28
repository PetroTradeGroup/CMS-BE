package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponOrigin;
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
        Long batchSequenceNumber,
        Integer batchSequence,
        Integer bookNumber,
        LocalDateTime createdAt,
        /** Legacy-imported numbers look identical to generated ones — this is the explicit tell. */
        CouponOrigin origin
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
                coupon.getBatch() == null ? null : coupon.getBatch().getSequenceNumber(),
                coupon.getBatchSequence(),
                coupon.getBookNumber(),
                coupon.getCreatedAt(),
                coupon.getOrigin()
        );
    }
}
