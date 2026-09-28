package com.couponnumbergenerator.enums;

/** One {@link com.couponnumbergenerator.model.CouponSaleLine}'s own outcome. */
public enum SaleLineStatus {
    /** Serials assigned for this line and its coupons flipped to ALLOCATED. */
    ASSIGNED,
    /** Not enough eligible stock at the sale's location for this line's fuel type + denomination. */
    FAILED
}
