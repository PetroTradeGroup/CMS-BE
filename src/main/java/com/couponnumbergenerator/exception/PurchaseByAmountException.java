package com.couponnumbergenerator.exception;

public class PurchaseByAmountException extends RuntimeException{
    public PurchaseByAmountException(String message) {
        super(message);
    }
}
