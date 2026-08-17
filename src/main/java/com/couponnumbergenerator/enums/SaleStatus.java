package com.couponnumbergenerator.enums;

/** Where a {@link com.couponnumbergenerator.model.CouponSale} is in the BC → CMS → BC round trip. */
public enum SaleStatus {
    /** Sale event received from BC; not yet processed. Never observed at rest — RECEIVED resolves to ASSIGNED or FAILED synchronously within the same request. */
    RECEIVED,
    /** Serials assigned and flipped to ALLOCATED; the assigned range hasn't been confirmed back to BC yet. */
    ASSIGNED,
    /** The assigned serial range was confirmed back to BC. Terminal — the sale is fully reconciled. */
    PUSHED,
    /** Not enough eligible stock at the requested location to fulfil the quantity. Terminal — needs manual restock/investigation. */
    FAILED
}