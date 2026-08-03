package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.model.Coupon;

import java.math.BigDecimal;

/** One coupon actually moved by a transfer — its position in the batch and its denomination. */
public record TransferredCouponResponse(
        String couponNumber,
        Integer batchSequence,
        BigDecimal denomination
) {
    public static TransferredCouponResponse from(Coupon coupon) {
        return new TransferredCouponResponse(coupon.getCouponNumber(), coupon.getBatchSequence(), coupon.getDenomination());
    }
}
