package com.couponnumbergenerator.repository.projection;

import java.math.BigDecimal;

/** How many coupons (and litres) one attendant redeemed over a date range. */
public interface RedemptionByAttendantRow {

    String getRequestedBy();

    long getCount();

    BigDecimal getLitres();
}
