package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.lifecycle.CouponStateMachine;

/**
 * What the attendant sees after scanning a coupon: its full details, whether it can be
 * redeemed right now, and — when it can't — a plain-language reason to read out or show.
 */
public record ScanResponse(
        CouponResponse coupon,
        boolean redeemable,
        String message
) {

    public static ScanResponse of(CouponResponse coupon) {
        boolean redeemable = CouponStateMachine.canTransition(coupon.status(), CouponStatus.REDEEMED);
        return new ScanResponse(coupon, redeemable, redeemable ? null : blockedMessage(coupon));
    }

    private static String blockedMessage(CouponResponse coupon) {
        return switch (coupon.status()) {
            case REDEEMED -> "Coupon %s has already been redeemed".formatted(coupon.couponNumber());
            case EXPIRED -> "Coupon %s has expired".formatted(coupon.couponNumber());
            case CANCELLED -> "Coupon %s has been cancelled".formatted(coupon.couponNumber());
            case FLAGGED -> "Coupon %s is flagged and cannot be redeemed — contact the issuer".formatted(coupon.couponNumber());
            default -> "Coupon %s cannot be redeemed in its current state (%s) — contact the issuer for a status change"
                    .formatted(coupon.couponNumber(), coupon.status());
        };
    }
}