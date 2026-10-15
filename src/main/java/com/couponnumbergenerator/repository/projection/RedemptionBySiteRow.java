package com.couponnumbergenerator.repository.projection;

import java.math.BigDecimal;

/** One site's redemptions over a range — the per-site lines of a company report. */
public interface RedemptionBySiteRow {

    Long getLocationId();

    String getLocationCode();

    String getLocationName();

    String getCompanyName();

    long getCount();

    BigDecimal getLitres();
}
