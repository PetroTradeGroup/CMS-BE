package com.couponnumbergenerator.exception;

public class CouponLocationMismatchException extends RuntimeException {

    public CouponLocationMismatchException(String couponNumber, String actualLocationCode, String assertedLocationCode) {
        super("Coupon %s is currently at location %s, not the redeeming location %s"
                .formatted(couponNumber, actualLocationCode, assertedLocationCode));
    }
}
