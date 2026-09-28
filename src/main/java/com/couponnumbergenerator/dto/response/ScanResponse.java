package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.lifecycle.CouponStateMachine;

import java.time.LocalDate;

/**
 * What the attendant sees after scanning a coupon: its full details, whether it can be
 * redeemed right now, and — when it can't — a plain-language reason to read out or show.
 */
public record ScanResponse(
        CouponResponse coupon,
        boolean redeemable,
        String message
) {

    /**
     * Redeemable only if its status allows it, it hasn't passed its expiry date, and it isn't
     * already in a pending redemption (presented at another pump or station — don't dispense).
     */
    public static ScanResponse of(CouponResponse coupon, boolean pendingRedemption, LocalDate today) {
        String blocked = blockedMessage(coupon, pendingRedemption, today);
        return new ScanResponse(coupon, blocked == null, blocked);
    }

    private static String blockedMessage(CouponResponse coupon, boolean pendingRedemption, LocalDate today) {
        if (!CouponStateMachine.canTransition(coupon.status(), CouponStatus.REDEEMED)) {
            return switch (coupon.status()) {
                case REDEEMED -> "Coupon %s has already been redeemed".formatted(coupon.couponNumber());
                case EXPIRED -> "Coupon %s has expired".formatted(coupon.couponNumber());
                case CANCELLED -> "Coupon %s has been cancelled".formatted(coupon.couponNumber());
                case FLAGGED -> "Coupon %s is flagged and cannot be redeemed — contact the issuer".formatted(coupon.couponNumber());
                default -> "Coupon %s cannot be redeemed in its current state (%s) — contact the issuer for a status change"
                        .formatted(coupon.couponNumber(), coupon.status());
            };
        }
        if (coupon.expiryDate() != null && coupon.expiryDate().isBefore(today)) {
            return "Coupon %s expired on %s".formatted(coupon.couponNumber(), coupon.expiryDate());
        }
        if (pendingRedemption) {
            return "Coupon %s has already been submitted for redemption — do not dispense".formatted(coupon.couponNumber());
        }
        return null;
    }
}
