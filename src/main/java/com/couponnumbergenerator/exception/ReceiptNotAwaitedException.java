package com.couponnumbergenerator.exception;

import com.couponnumbergenerator.enums.ApprovalStatus;

/** Receipt confirmation attempted on an approval request that isn't TRANSFERSHIPMENT. */
public class ReceiptNotAwaitedException extends RuntimeException {

    public ReceiptNotAwaitedException(Long id, ApprovalStatus status) {
        super("Approval request %d is not awaiting receipt confirmation (status: %s)".formatted(id, status));
    }
}