package com.couponnumbergenerator.enums;

/**
 * PENDING → COMMERCIAL_APPROVED → APPROVED → (PARTIALLY_FULFILLED)* → FULFILLED, or REJECTED from any
 * non-terminal status. Commercial (Manager or Rep) signs off first, then the Stocks Controller; only an
 * APPROVED (or PARTIALLY_FULFILLED) requisition can be issued against by a Stocks Clerk.
 */
public enum RequisitionStatus {
    /** Raised by a Sales Clerk, awaiting the Commercial Manager's / Rep's approval. */
    PENDING,
    /** Commercial approved, awaiting the Stocks Controller's approval. */
    COMMERCIAL_APPROVED,
    /** Stocks Controller approved — a Stocks Clerk can now issue (GIV) against it. */
    APPROVED,
    PARTIALLY_FULFILLED,
    FULFILLED,
    REJECTED
}
