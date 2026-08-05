package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.response.RedemptionDailySummaryResponse;

import java.time.LocalDate;

public interface RedemptionReportService {

    /**
     * What was redeemed on {@code date} (times are interpreted in the server's zone), optionally
     * limited to one site: coupon counts and litres, in total and broken down by fuel type and
     * denomination. Days with no redemptions return zero totals and an empty breakdown.
     */
    RedemptionDailySummaryResponse dailySummary(LocalDate date, Long locationId);
}