package com.couponnumbergenerator.repository.projection;

import com.couponnumbergenerator.enums.CouponStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Litres that moved from one status to another on one day — the deltas the balance trend is rolled forward with. */
public interface StatusFlowRow {

    LocalDate getDay();

    CouponStatus getFromStatus();

    CouponStatus getToStatus();

    BigDecimal getLitres();
}
