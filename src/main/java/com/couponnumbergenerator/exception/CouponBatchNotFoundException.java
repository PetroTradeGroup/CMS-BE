package com.couponnumbergenerator.exception;

public class CouponBatchNotFoundException extends RuntimeException {

    public CouponBatchNotFoundException(Long id) {
        super("Coupon batch not found with id: " + id);
    }
}