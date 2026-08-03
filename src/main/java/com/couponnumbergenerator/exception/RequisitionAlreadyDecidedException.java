package com.couponnumbergenerator.exception;

import com.couponnumbergenerator.enums.RequisitionStatus;

public class RequisitionAlreadyDecidedException extends RuntimeException {

    public RequisitionAlreadyDecidedException(Long id, RequisitionStatus status) {
        super("Requisition %d is already %s".formatted(id, status));
    }
}