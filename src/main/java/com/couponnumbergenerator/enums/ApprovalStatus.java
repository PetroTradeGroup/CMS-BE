package com.couponnumbergenerator.enums;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    /** Supervisor approved a department-changing transfer; coupons are shipped, awaiting the receiving department's confirmation. */
    TRANSFERSHIPMENT,
    /** The receiving department confirmed receipt; the move is fully applied. */
    TRANSRECEIPT,
    /** A REDEMPTION request was posted with a Navision document number; the coupons are REDEEMED. */
    POSTED,
    REJECTED
}