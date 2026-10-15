package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.RedemptionByAttendantResponse;
import com.couponnumbergenerator.dto.response.RedemptionByAttendantResponse.AttendantLine;
import com.couponnumbergenerator.dto.response.RedemptionLogEntry;
import com.couponnumbergenerator.dto.response.RedemptionLogTotals;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.DenominationLine;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.FuelTypeSummary;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.SiteLine;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.exception.CompanyNotFoundException;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.AppUser;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.AppUserRepository;
import com.couponnumbergenerator.repository.CompanyRepository;
import com.couponnumbergenerator.repository.CouponApprovalRequestRepository;
import com.couponnumbergenerator.repository.projection.RedemptionLogTotalsRow;
import com.couponnumbergenerator.repository.CouponMovementRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.repository.projection.RedemptionByAttendantRow;
import com.couponnumbergenerator.repository.projection.RedemptionSummaryRow;
import com.couponnumbergenerator.security.LocationAccessGuard;
import com.couponnumbergenerator.service.RedemptionReportService;
import com.couponnumbergenerator.specification.ApprovalRequestSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedemptionReportServiceImpl implements RedemptionReportService {

    private final CouponMovementRepository couponMovementRepository;
    private final CouponApprovalRequestRepository approvalRequestRepository;
    private final CouponRepository couponRepository;
    private final LocationRepository locationRepository;
    private final CompanyRepository companyRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final AppUserRepository appUserRepository;
    private final LocationAccessGuard locationAccessGuard;

    @Override
    @Transactional(readOnly = true)
    public RedemptionSummaryResponse summary(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId,
                                             Long fuelTypeId, Long companyId, CouponType couponType) {
        validateRange(dateFrom, dateTo);
        validateFuelType(fuelTypeId);
        ScopeFilters scope = resolveScope(requestedLocationId, companyId);
        return buildSummary(dateFrom, dateTo, scope.locationId(), fuelTypeId, scope.companyId(), couponType,
                scope.requestedBy(), null);
    }

    @Override
    @Transactional(readOnly = true)
    public RedemptionSummaryResponse summaryForAttendant(LocalDate dateFrom, LocalDate dateTo, String attendantUsername, Long fuelTypeId,
                                                         CouponType couponType) {
        validateRange(dateFrom, dateTo);
        validateFuelType(fuelTypeId);
        // Team Leader only (enforced by @PreAuthorize on the controller) — the site is always
        // their own station, mirroring byAttendant.
        Location callerLocation = callerLocation(SecurityContextHolder.getContext().getAuthentication());
        return buildSummary(dateFrom, dateTo, callerLocation.getId(), fuelTypeId, null, couponType, attendantUsername,
                attendantUsername);
    }

    /** requestedBy filters the rows; attendantUsername is only echoed back (set when the report is about one attendant). */
    private RedemptionSummaryResponse buildSummary(LocalDate dateFrom, LocalDate dateTo, Long locationId, Long fuelTypeId,
                                                   Long companyId, CouponType couponType, String requestedBy,
                                                   String attendantUsername) {
        LocalDateTime start = dateFrom.atStartOfDay();
        LocalDateTime end = dateTo.plusDays(1).atStartOfDay();
        List<FuelTypeSummary> byFuelType = groupByFuelType(couponMovementRepository.summarizeRedemptions(
                start, end, locationId, requestedBy, fuelTypeId, companyId, couponType));
        List<SiteLine> bySite = couponMovementRepository.summarizeRedemptionsBySite(
                        start, end, locationId, requestedBy, fuelTypeId, companyId, couponType).stream()
                .map(row -> new SiteLine(row.getLocationId(), row.getLocationCode(), row.getLocationName(),
                        row.getCompanyName(), row.getCount(), row.getLitres()))
                .toList();
        long totalCoupons = byFuelType.stream().mapToLong(FuelTypeSummary::count).sum();
        BigDecimal totalLitres = byFuelType.stream().map(FuelTypeSummary::litres).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RedemptionSummaryResponse(dateFrom, dateTo, locationId, fuelTypeId, companyId,
                companyName(companyId, locationId), attendantUsername, couponType, totalCoupons, totalLitres, byFuelType,
                bySite);
    }

    /** The company filtered on, else the company of the one site in scope, else null (every company). */
    private String companyName(Long companyId, Long locationId) {
        if (companyId != null) {
            return companyRepository.findById(companyId).map(c -> c.getName()).orElse(null);
        }
        return locationId == null ? null
                : locationRepository.findById(locationId).map(l -> l.getCompany().getName()).orElse(null);
    }

    /** Rows must arrive ordered by fuel type then denomination, so each fuel type's lines are contiguous. */
    static List<FuelTypeSummary> groupByFuelType(List<? extends RedemptionSummaryRow> rows) {
        List<FuelTypeSummary> byFuelType = new ArrayList<>();
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
        }
        return byFuelType;
    }

    @Override
    @Transactional(readOnly = true)
    public RedemptionByAttendantResponse byAttendant(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId,
                                                     String attendantUsername, Long fuelTypeId, CouponType couponType) {
        validateRange(dateFrom, dateTo);
        validateFuelType(fuelTypeId);
        // Team Leader (station-scoped) is pinned to their own site; a Regional Rep picks one, or none for
        // every site. attendantUsername is a free choice either way — it narrows, it doesn't widen.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long locationId = locationAccessGuard.callerLocationCode(authentication) != null
                ? callerLocation(authentication).getId()
                : validLocation(requestedLocationId);

        List<RedemptionByAttendantRow> rows = couponMovementRepository.summarizeRedemptionsByAttendant(
                dateFrom.atStartOfDay(), dateTo.plusDays(1).atStartOfDay(), locationId, attendantUsername, fuelTypeId,
                couponType);
        Map<String, String> names = attendantNames(rows.stream().map(RedemptionByAttendantRow::getRequestedBy).toList());

        List<AttendantLine> byAttendant = new ArrayList<>();
        long totalCoupons = 0;
        BigDecimal totalLitres = BigDecimal.ZERO;
        for (RedemptionByAttendantRow row : rows) {
            byAttendant.add(new AttendantLine(row.getRequestedBy(), names.get(row.getRequestedBy()), row.getCount(), row.getLitres()));
            totalCoupons += row.getCount();
            totalLitres = totalLitres.add(row.getLitres());
        }
        return new RedemptionByAttendantResponse(
                dateFrom, dateTo, locationId, fuelTypeId, totalCoupons, totalLitres, byAttendant);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<RedemptionLogEntry> log(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId,
                                                 Long companyId, String attendantUsername, CouponType couponType,
                                                 Pageable pageable) {
        validateRange(dateFrom, dateTo);
        ScopeFilters scope = resolveScope(requestedLocationId, companyId);
        // A plain Attendant is always narrowed to themselves; anyone else may pick an attendant.
        String requestedBy = scope.requestedBy() != null ? scope.requestedBy() : attendantUsername;
        Page<CouponApprovalRequest> page = approvalRequestRepository.findAll(ApprovalRequestSpecification.redemptionLog(
                dateFrom.atStartOfDay(), dateTo.plusDays(1).atStartOfDay(), scope.locationId(), scope.companyId(), requestedBy,
                couponType),
                pageable);

        // Two lookups for the whole page rather than per row: fuel types from the coupons, names from the attendant register.
        Map<String, String> fuelByCoupon = couponRepository.findByCouponNumberIn(page.getContent().stream()
                        .flatMap(r -> r.getCouponNumbers().stream()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Coupon::getCouponNumber, c -> c.getFuelType().getName()));
        Map<String, String> names = attendantNames(page.getContent().stream().map(CouponApprovalRequest::getRequestedBy).toList());

        return PagedResponse.from(page.map(r -> toLogEntry(r, fuelByCoupon, names)));
    }

    @Override
    @Transactional(readOnly = true)
    public RedemptionLogTotals logTotals(LocalDate dateFrom, LocalDate dateTo, Long requestedLocationId,
                                         Long companyId, String attendantUsername, CouponType couponType) {
        validateRange(dateFrom, dateTo);
        ScopeFilters scope = resolveScope(requestedLocationId, companyId);
        String requestedBy = scope.requestedBy() != null ? scope.requestedBy() : attendantUsername;
        RedemptionLogTotalsRow row = approvalRequestRepository.summarizeRedemptionLog(dateFrom.atStartOfDay(),
                dateTo.plusDays(1).atStartOfDay(), scope.locationId(), scope.companyId(), requestedBy, couponType);
        return new RedemptionLogTotals(dateFrom, dateTo, row.getRedemptions(), row.getCoupons(), row.getLitres());
    }

    private RedemptionLogEntry toLogEntry(CouponApprovalRequest r, Map<String, String> fuelByCoupon, Map<String, String> names) {
        Location site = r.getToLocation();
        BigDecimal litres = r.getDenominationBreakdown().stream()
                .map(d -> d.getDenomination().multiply(BigDecimal.valueOf(d.getCount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> fuelTypes = r.getCouponNumbers().stream().map(fuelByCoupon::get)
                .filter(Objects::nonNull).distinct().sorted().toList();
        return new RedemptionLogEntry(r.getId(), r.getRequestedAt(), r.getStatus(), r.getDecidedAt(),
                r.getDocumentNumber(), r.getCarRegistrationNumber(), r.getRequestedBy(), names.get(r.getRequestedBy()),
                site == null ? null : site.getId(), site == null ? null : site.getCode(),
                site == null ? null : site.getName(), site == null ? null : site.getCompany().getName(),
                r.getCouponCount(), litres, fuelTypes);
    }

    /** username → "First Last" for those in the attendant register with a name on file. */
    private Map<String, String> attendantNames(Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return appUserRepository.findByUsernameIn(usernames).stream()
                .filter(u -> fullName(u) != null)
                .collect(Collectors.toMap(AppUser::getUsername, this::fullName, (a, b) -> a));
    }

    private String fullName(AppUser user) {
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? null : name;
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

    private Long validLocation(Long locationId) {
        if (locationId != null && !locationRepository.existsById(locationId)) {
            throw new LocationNotFoundException(locationId);
        }
        return locationId;
    }

    private Location callerLocation(Authentication authentication) {
        String callerLocationCode = locationAccessGuard.callerLocationCode(authentication);
        return locationRepository.findByCode(callerLocationCode)
                .orElseThrow(() -> new LocationNotFoundException(callerLocationCode));
    }

    /**
     * Station-scoped caller (Attendant/Team Leader): the token decides the site, not the query
     * params — they can't probe another site's (or company's) totals by passing a different
     * locationId/companyId. Team Leader sees the whole site; a plain Attendant is narrowed further
     * to just what they personally submitted for redemption. Non-station callers (Admin, Regional
     * Rep, etc.) keep the free-form site and company filters, unfiltered by requestedBy.
     */
    private ScopeFilters resolveScope(Long requestedLocationId, Long requestedCompanyId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (locationAccessGuard.callerLocationCode(authentication) != null) {
            String requestedByFilter = locationAccessGuard.callerIsTeamLeader(authentication)
                    ? null
                    : locationAccessGuard.callerUsername(authentication);
            return new ScopeFilters(callerLocation(authentication).getId(), null, requestedByFilter);
        }
        if (requestedCompanyId != null && !companyRepository.existsById(requestedCompanyId)) {
            throw new CompanyNotFoundException(requestedCompanyId);
        }
        return new ScopeFilters(validLocation(requestedLocationId), requestedCompanyId, null);
    }

    private record ScopeFilters(Long locationId, Long companyId, String requestedBy) {}
}
