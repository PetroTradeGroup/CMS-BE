package com.couponnumbergenerator.enums;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    /** Supervisor approved a department-changing transfer; coupons are shipped, awaiting the receiving department's confirmation. */
    TRANSFERSHIPMENT,
    /** The receiving department confirmed receipt; the move is fully applied. */
    TRANSRECEIPT,
    REJECTED
}