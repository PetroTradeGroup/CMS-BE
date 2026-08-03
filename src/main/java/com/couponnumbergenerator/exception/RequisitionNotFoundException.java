package com.couponnumbergenerator.exception;

public class RequisitionNotFoundException extends RuntimeException {

    public RequisitionNotFoundException(Long id) {
        super("Requisition not found with id: " + id);
    }
}