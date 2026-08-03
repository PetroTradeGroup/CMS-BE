package com.couponnumbergenerator.repository.projection;

import com.couponnumbergenerator.enums.CouponStatus;

public interface InventorySummaryRow {

    Long getLocationId();

    String getLocationCode();

    String getLocationName();

    Long getFuelTypeId();

    String getFuelTypeName();

    CouponStatus getStatus();

    long getCount();
}