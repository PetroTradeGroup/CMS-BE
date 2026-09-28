package com.couponnumbergenerator.exception;

public class BankPurchaseNotFoundException extends RuntimeException {

    public BankPurchaseNotFoundException(String bankCode, String bankReference) {
        super("No purchase %s found for bank %s".formatted(bankReference, bankCode));
    }
}
