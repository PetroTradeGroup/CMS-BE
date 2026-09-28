package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.response.RedemptionByAttendantResponse;
import com.couponnumbergenerator.dto.response.RedemptionByAttendantResponse.AttendantLine;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.DenominationLine;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.FuelTypeSummary;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.CouponMovementRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.repository.projection.RedemptionByAttendantRow;
import com.couponnumbergenerator.repository.projection.RedemptionSummaryRow;
import com.couponnumbergenerator.security.LocationAccessGuard;
import com.couponnumbergenerator.service.RedemptionReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedemptionReportServiceImpl implements RedemptionReportService {

    private final CouponMovementRepository couponMovementRepository;
    private final LocationRepository locationRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final LocationAccessGuard locationAccessGuard;

    @Override
    @Transactional(readOnly = true)
    public RedemptionSummaryResponse summary(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId, Long fuelTypeId) {
        validateRange(dateFrom, dateTo);
        validateFuelType(fuelTypeId);
        ScopeFilters scope = resolveScope(requestedLocationId);
        List<RedemptionSummaryRow> rows = couponMovementRepository.summarizeRedemptions(
                dateFrom.atStartOfDay(), dateTo.plusDays(1).atStartOfDay(),
                scope.locationId(), scope.requestedBy(), fuelTypeId);
        return buildSummary(dateFrom, dateTo, scope.locationId(), fuelTypeId, null, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public RedemptionSummaryResponse summaryForAttendant(LocalDate dateFrom, LocalDate dateTo, String attendantUsername, Long fuelTypeId) {
        validateRange(dateFrom, dateTo);
        validateFuelType(fuelTypeId);
        // Team Leader only (enforced by @PreAuthorize on the controller) — the site is always
        // their own station, mirroring byAttendant.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String callerLocationCode = locationAccessGuard.callerLocationCode(authentication);
        Location callerLocation = locationRepository.findByCode(callerLocationCode)
                .orElseThrow(() -> new LocationNotFoundException(callerLocationCode));

        List<RedemptionSummaryRow> rows = couponMovementRepository.summarizeRedemptions(
                dateFrom.atStartOfDay(), dateTo.plusDays(1).atStartOfDay(),
                callerLocation.getId(), attendantUsername, fuelTypeId);
        return buildSummary(dateFrom, dateTo, callerLocation.getId(), fuelTypeId, attendantUsername, rows);
    }

    /** Rows arrive ordered by fuel type then denomination, so each fuel type's lines are contiguous. */
    private RedemptionSummaryResponse buildSummary(LocalDate dateFrom, LocalDate dateTo, Long locationId,
            Long fuelTypeId, String attendantUsername, List<RedemptionSummaryRow> rows) {
        List<FuelTypeSummary> byFuelType = new ArrayList<>();
        long totalCoupons = 0;
        BigDecimal totalLitres = BigDecimal.ZERO;
        int i = 0;
        while (i < rows.size()) {
            RedemptionSummaryRow first = rows.get(i);
            List<DenominationLine> lines = new ArrayList<>();
            long count = 0;
            BigDecimal litres = BigDecimal.ZERO;
            while (i < rows.size() && rows.get(i).getFuelTypeId().equals(first.getFuelTypeId())) {
                RedemptionSummaryRow row = rows.get(i);
                lines.add(new DenominationLine(row.getDenomination(), row.getCount(), row.getLitres()));
                count += row.getCount();
                litres = litres.add(row.getLitres());
                i++;
            }
            byFuelType.add(new FuelTypeSummary(first.getFuelTypeId(), first.getFuelTypeName(), count, litres, lines));
            totalCoupons += count;
            totalLitres = totalLitres.add(litres);
        }
        return new RedemptionSummaryResponse(
                dateFrom, dateTo, locationId, fuelTypeId, attendantUsername, totalCoupons, totalLitres, byFuelType);
    }

    @Override
    @Transactional(readOnly = true)
    public RedemptionByAttendantResponse byAttendant(LocalDate dateFrom, LocalDate dateTo, String attendantUsername, Long fuelTypeId) {
        validateRange(dateFrom, dateTo);
        validateFuelType(fuelTypeId);
        // Team Leader only (enforced by @PreAuthorize on the controller) — the site is always
        // their own station, never client-supplied. attendantUsername, unlike the self-restriction
        // used elsewhere in this class, is a free choice: a Team Leader may look at any one
        // attendant at their site, or omit it to see everyone.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String callerLocationCode = locationAccessGuard.callerLocationCode(authentication);
        Location callerLocation = locationRepository.findByCode(callerLocationCode)
                .orElseThrow(() -> new LocationNotFoundException(callerLocationCode));

        List<RedemptionByAttendantRow> rows = couponMovementRepository.summarizeRedemptionsByAttendant(
                dateFrom.atStartOfDay(), dateTo.plusDays(1).atStartOfDay(),
                callerLocation.getId(), attendantUsername, fuelTypeId);

        List<AttendantLine> byAttendant = new ArrayList<>();
        long totalCoupons = 0;
        BigDecimal totalLitres = BigDecimal.ZERO;
        for (RedemptionByAttendantRow row : rows) {
            byAttendant.add(new AttendantLine(row.getRequestedBy(), row.getCount(), row.getLitres()));
            totalCoupons += row.getCount();
            totalLitres = totalLitres.add(row.getLitres());
        }
        return new RedemptionByAttendantResponse(
                dateFrom, dateTo, callerLocation.getId(), fuelTypeId, totalCoupons, totalLitres, byAttendant);
    }

    private void validateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom (%s) must not be after dateTo (%s)".formatted(dateFrom, dateTo));
        }
    }

    private void validateFuelType(Long fuelTypeId) {
        if (fuelTypeId != null && !fuelTypeRepository.existsById(fuelTypeId)) {
            throw new FuelTypeNotFoundException(fuelTypeId);
        }
    }

    /**
     * Station-scoped caller (Attendant/Team Leader): the token decides the site, not the query
     * param — they can't probe another site's totals by passing a different locationId. Team
     * Leader sees the whole site; a plain Attendant is narrowed further to just what they
     * personally submitted for redemption. Non-station callers (Admin, etc.) keep the free-form
     * requestedLocationId, unfiltered by requestedBy.
     */
    private ScopeFilters resolveScope(Long requestedLocationId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String callerLocationCode = locationAccessGuard.callerLocationCode(authentication);

        if (callerLocationCode != null) {
            Location callerLocation = locationRepository.findByCode(callerLocationCode)
                    .orElseThrow(() -> new LocationNotFoundException(callerLocationCode));
            String requestedByFilter = locationAccessGuard.callerIsTeamLeader(authentication)
                    ? null
                    : locationAccessGuard.callerUsername(authentication);
            return new ScopeFilters(callerLocation.getId(), requestedByFilter);
        }
        if (requestedLocationId != null && !locationRepository.existsById(requestedLocationId)) {
            throw new LocationNotFoundException(requestedLocationId);
        }
        return new ScopeFilters(requestedLocationId, null);
    }

    private record ScopeFilters(Long locationId, String requestedBy) {}
}
