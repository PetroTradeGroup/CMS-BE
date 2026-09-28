package com.couponnumbergenerator.enums;

/** Where a {@link com.couponnumbergenerator.model.CouponSale} is in the BC → CMS → BC round trip. */
public enum SaleStatus {
    /** Sale event received from BC; not yet processed. Never observed at rest — RECEIVED resolves to ASSIGNED / PARTIALLY_ASSIGNED / FAILED synchronously within the same request. */
    RECEIVED,
    /** Every line got its serials and its coupons were flipped to ALLOCATED; the assigned range hasn't been confirmed back to BC yet. */
    ASSIGNED,
    /** Some lines were assigned and some FAILED on stock. The assigned lines still need confirming back to BC. */
    PARTIALLY_ASSIGNED,
    /** The assigned serial range was confirmed back to BC. Terminal — note a PUSHED sale can still carry FAILED lines needing a restock. */
    PUSHED,
    /** No line could be filled — not enough eligible stock at the requested location for any of them. Terminal — needs manual restock/investigation. */
    FAILED
}
