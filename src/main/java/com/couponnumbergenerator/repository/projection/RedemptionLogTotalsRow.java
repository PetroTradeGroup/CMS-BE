package com.couponnumbergenerator.repository.projection;

import java.math.BigDecimal;

/** Totals over every redemption the log's filters match (not just one page). */
public interface RedemptionLogTotalsRow {

    long getRedemptions();

    long getCoupons();

    BigDecimal getLitres();
}
