package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.response.RedemptionByAttendantResponse;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse;

import java.time.LocalDate;

public interface RedemptionReportService {

    /**
     * What was redeemed from {@code dateFrom} to {@code dateTo} inclusive (times are interpreted
     * in the server's zone), optionally limited to one site and/or one fuel type: coupon counts
     * and litres, in total and broken down by fuel type and denomination. A range with no
     * redemptions returns zero totals and an empty breakdown. For a station-scoped caller
     * (Attendant/Team Leader), {@code requestedLocationId} is ignored — the site comes from their
     * token instead, and a plain Attendant is further narrowed to only what they personally
     * redeemed.
     */
    RedemptionSummaryResponse summary(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId, Long fuelTypeId);

    /**
     * Who redeemed what, from {@code dateFrom} to {@code dateTo} inclusive, at the caller's own
     * site — Team Leader only (see {@code RedemptionController}). {@code attendantUsername}
     * optionally narrows to one attendant; {@code fuelTypeId} optionally narrows to one fuel type.
     */
    RedemptionByAttendantResponse byAttendant(LocalDate dateFrom, LocalDate dateTo, String attendantUsername, Long fuelTypeId);

    /**
     * What one attendant redeemed at the caller's own site, from {@code dateFrom} to {@code dateTo}
     * inclusive — the same fuel type/denomination breakdown as {@link #summary}, narrowed to a
     * single attendant. Team Leader only; the site always comes from their token, never a param.
     * A range with no matching redemptions returns zero totals and an empty breakdown, same as
     * {@link #summary} — no separate check that the username belongs to this site.
     */
    RedemptionSummaryResponse summaryForAttendant(LocalDate dateFrom, LocalDate dateTo, String attendantUsername, Long fuelTypeId);
}
