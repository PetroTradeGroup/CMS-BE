package com.couponnumbergenerator.enums;

/**
 * How a coupon entered the system. Legacy-imported numbers use the same format as
 * system-generated ones, so this is the explicit signal — the coupon number alone can't tell
 * them apart.
 */
public enum CouponOrigin {
    GENERATED,
    LEGACY_IMPORT
}
