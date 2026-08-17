package com.couponnumbergenerator.exception;

public class CouponSaleNotFoundException extends RuntimeException {

    public CouponSaleNotFoundException(String bcDocumentNumber) {
        super("No sale found for BC document number: " + bcDocumentNumber);
    }
}