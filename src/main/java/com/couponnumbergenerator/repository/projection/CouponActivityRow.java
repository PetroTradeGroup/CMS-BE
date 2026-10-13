package com.couponnumbergenerator.repository.projection;

import com.couponnumbergenerator.enums.CouponStatus;

/** One (status, fuel type, denomination) line: how many coupons moved into that status, and their litres. */
public interface CouponActivityRow extends RedemptionSummaryRow {

    CouponStatus getStatus();
}
