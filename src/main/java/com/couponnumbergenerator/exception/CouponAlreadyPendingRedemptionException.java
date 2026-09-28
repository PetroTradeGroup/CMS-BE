package com.couponnumbergenerator.exception;

import java.util.List;

/** The coupon is already in a PENDING redemption — it was presented somewhere else, so don't dispense again. */
public class CouponAlreadyPendingRedemptionException extends RuntimeException {

    public CouponAlreadyPendingRedemptionException(List<String> couponNumbers) {
        super("Already submitted for redemption: " + String.join(", ", couponNumbers));
    }
}
