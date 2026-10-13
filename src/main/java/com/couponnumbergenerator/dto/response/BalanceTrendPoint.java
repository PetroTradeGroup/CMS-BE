package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Litres held in each status at the end of {@code date}. IN_STOCK is stock on hand; ALLOCATED is what's owed (sold, not yet redeemed). */
public record BalanceTrendPoint(LocalDate date, Map<CouponStatus, BigDecimal> litres) {}
