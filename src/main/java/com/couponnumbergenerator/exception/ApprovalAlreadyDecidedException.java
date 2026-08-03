package com.couponnumbergenerator.exception;

import com.couponnumbergenerator.enums.ApprovalStatus;

public class ApprovalAlreadyDecidedException extends RuntimeException {

    public ApprovalAlreadyDecidedException(Long id, ApprovalStatus status) {
        super("Approval request %d was already %s".formatted(id, status));
    }
}
