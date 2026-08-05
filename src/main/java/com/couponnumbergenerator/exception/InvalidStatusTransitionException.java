package com.couponnumbergenerator.exception;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.lifecycle.CouponStateMachine;

import java.util.Set;

public class InvalidStatusTransitionException extends RuntimeException {

    /** Not-yet-allocated states: redemption fails only because the issuer hasn't allocated the coupon yet. */
    private static final Set<CouponStatus> AWAITING_ALLOCATION =
            Set.of(CouponStatus.GENERATED, CouponStatus.IN_STOCK, CouponStatus.IN_TRANSIT);

    public InvalidStatusTransitionException(String couponNumber, CouponStatus from, CouponStatus to) {
        super(buildMessage(couponNumber, from, to));
    }

    /**
     * A coupon already sitting in the requested target status (most commonly REDEEMED, a terminal
     * state, so "transition" to it again is really just a duplicate attempt) gets a plain-language
     * message instead of the generic "illegal transition" phrasing, which reads oddly for a no-op
     * self-transition (e.g. "REDEEMED -> REDEEMED"). Likewise redeeming a coupon that hasn't
     * been allocated yet (GENERATED/IN_STOCK/IN_TRANSIT) — that surfaces on the redemption
     * device at the site, so it gets attendant-facing wording pointing at the fix (the issuer
     * allocating it) instead of state-machine jargon.
     */
    private static String buildMessage(String couponNumber, CouponStatus from, CouponStatus to) {
        if (from == to) {
            return from == CouponStatus.REDEEMED
                    ? "Coupon %s has already been redeemed".formatted(couponNumber)
                    : "Coupon %s is already %s".formatted(couponNumber, from);
        }
        if (to == CouponStatus.REDEEMED && AWAITING_ALLOCATION.contains(from)) {
            return "Coupon %s cannot be redeemed in its current state (%s) — contact the issuer for a status change"
                    .formatted(couponNumber, from);
        }
        return "Illegal status transition for coupon %s: %s -> %s (allowed targets: %s)"
                .formatted(couponNumber, from, to, CouponStateMachine.allowedTargets(from));
    }

    /** A pure location/department reassignment (no status change) attempted on a coupon that's no longer in stock. */
    public InvalidStatusTransitionException(String couponNumber, CouponStatus from) {
        super("Coupon %s cannot be transferred while %s — it is not in stock, so this operation can't be performed"
                .formatted(couponNumber, from));
    }

    /** A pure reassignment attempted on a coupon that's IN_STOCK but sitting outside the stock department. */
    public InvalidStatusTransitionException(String couponNumber, String currentDepartmentCode, String requiredDepartmentCode) {
        super("Coupon %s cannot be transferred — it is in department %s, not %s, so this operation can't be performed"
                .formatted(couponNumber, currentDepartmentCode, requiredDepartmentCode));
    }
}