package com.couponnumbergenerator.repository.projection;

import java.math.BigDecimal;

/** One (fuel type, denomination) line of a day's redemptions: how many coupons and their litres. */
public interface RedemptionSummaryRow {

    Long getFuelTypeId();

    String getFuelTypeName();

    BigDecimal getDenomination();

    long getCount();

    BigDecimal getLitres();
}