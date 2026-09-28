package com.couponnumbergenerator.enums;

/** Where a {@link com.couponnumbergenerator.model.BankPurchase} stands. */
public enum BankPurchaseStatus {
    /** Coupons minted and ALLOCATED to the customer. */
    ISSUED,
    /** Bank reversed the purchase (refund / failed payment); every coupon was CANCELLED. Terminal. */
    REVERSED
}
