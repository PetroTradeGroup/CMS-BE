package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.RedemptionByAttendantResponse;
import com.couponnumbergenerator.dto.response.RedemptionLogEntry;
import com.couponnumbergenerator.dto.response.RedemptionLogTotals;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse;
import com.couponnumbergenerator.enums.CouponType;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface RedemptionReportService {

    /**
     * What was redeemed from {@code dateFrom} to {@code dateTo} inclusive (times are interpreted
     * in the server's zone), optionally limited to one site, one company's sites, one fuel type and/or one coupon type (PHYSICAL/DIGITAL; null = both):
     * coupon counts and litres, in total and broken down by fuel type/denomination and by site. A range
     * with no redemptions returns zero totals and empty breakdowns. For a station-scoped caller
     * (Attendant/Team Leader), {@code requestedLocationId} and {@code companyId} are ignored — the site
     * comes from their token instead, and a plain Attendant is further narrowed to only what they
     * personally redeemed.
     */
    RedemptionSummaryResponse summary(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId, Long fuelTypeId, Long companyId,
                                      CouponType couponType);

    /**
     * Who redeemed what, from {@code dateFrom} to {@code dateTo} inclusive, with each attendant's name.
     * A Team Leader always gets their own site (requestedLocationId ignored); a Regional Rep picks any
     * site, or omits it for every site. {@code attendantUsername} / {@code fuelTypeId} optionally narrow it.
     */
    RedemptionByAttendantResponse byAttendant(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId,
                                              String attendantUsername, Long fuelTypeId, CouponType couponType);

    /**
     * What one attendant redeemed at the caller's own site, from {@code dateFrom} to {@code dateTo}
     * inclusive — the same fuel type/denomination breakdown as {@link #summary}, narrowed to a
     * single attendant. Team Leader only; the site always comes from their token, never a param.
     * A range with no matching redemptions returns zero totals and an empty breakdown, same as
     * {@link #summary} — no separate check that the username belongs to this site.
     */
    RedemptionSummaryResponse summaryForAttendant(LocalDate dateFrom, LocalDate dateTo, String attendantUsername, Long fuelTypeId,
                                                  CouponType couponType);

    /**
     * Every redemption submitted in the range, newest first, with its exact time, attendant, site, company,
     * vehicle, coupons, litres and fuel type(s). Same station scoping as {@link #summary}: a Team Leader sees
     * their site, a plain Attendant only their own; others filter freely by site, company and attendant.
     */
    PagedResponse<RedemptionLogEntry> log(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId, Long companyId,
                                          String attendantUsername, CouponType couponType, Pageable pageable);

    /** Totals for {@link #log}'s filters and scoping across the whole range, REJECTED redemptions excluded. */
    RedemptionLogTotals logTotals(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId, Long companyId,
                                  String attendantUsername, CouponType couponType);
}
