package com.couponnumbergenerator.exception;

/** The ERP could not be reached or gave an unusable answer; the redemption stays PENDING for retry. */
public class ErpIntegrationException extends RuntimeException {

    public ErpIntegrationException(String message) {
        super(message);
    }

    public ErpIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}