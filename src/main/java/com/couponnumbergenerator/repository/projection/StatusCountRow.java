package com.couponnumbergenerator.repository.projection;

import com.couponnumbergenerator.enums.CouponStatus;

public interface StatusCountRow {

    CouponStatus getStatus();

    long getCount();
}