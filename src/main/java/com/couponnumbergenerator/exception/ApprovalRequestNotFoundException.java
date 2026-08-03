package com.couponnumbergenerator.exception;

public class ApprovalRequestNotFoundException extends RuntimeException {

    public ApprovalRequestNotFoundException(Long id) {
        super("Approval request not found with id: " + id);
    }
}
