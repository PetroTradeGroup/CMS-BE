package com.couponnumbergenerator.exception;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.lifecycle.CouponStateMachine;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String couponNumber, CouponStatus from, CouponStatus to) {
        super("Illegal status transition for coupon %s: %s -> %s (allowed targets: %s)"
                .formatted(couponNumber, from, to, CouponStateMachine.allowedTargets(from)));
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