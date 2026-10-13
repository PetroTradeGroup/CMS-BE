package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.response.BalanceTrendPoint;
import com.couponnumbergenerator.dto.response.CouponActivityResponse;
import com.couponnumbergenerator.dto.response.InventorySummaryResponse;
import com.couponnumbergenerator.enums.CouponStatus;

import java.time.LocalDate;
import java.util.List;

public interface InventoryService {

    /** Stock-on-hand grouped by location, fuel type and status; each filter is optional. */
    List<InventorySummaryResponse> summarize(Long locationId, Long fuelTypeId, CouponStatus status);

    /**
     * Coupons that moved into each status from {@code dateFrom} to {@code dateTo} inclusive (server zone),
     * grouped status → fuel type → denomination with counts and litres. status, locationId (the site the
     * coupons moved to) and fuelTypeId are optional filters.
     */
    CouponActivityResponse activity(LocalDate dateFrom, LocalDate dateTo, CouponStatus status, Long locationId, Long fuelTypeId);

    /**
     * Where coupons stood at the end of {@code asAt}: litres per status (IN_STOCK = stock on hand, ALLOCATED = owed),
     * same breakdown as {@link #activity}. dateFrom = dateTo = asAt in the response.
     */
    CouponActivityResponse balance(LocalDate asAt, CouponStatus status, Long locationId, Long fuelTypeId);

    /** End-of-day litres per status for every day from {@code dateFrom} to {@code dateTo} inclusive (at most 366 days). */
    List<BalanceTrendPoint> balanceTrend(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId);
}
