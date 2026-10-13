package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.response.BalanceTrendPoint;
import com.couponnumbergenerator.dto.response.CouponActivityResponse;
import com.couponnumbergenerator.dto.response.CouponActivityResponse.StatusSummary;
import com.couponnumbergenerator.dto.response.InventorySummaryResponse;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.FuelTypeSummary;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.repository.CouponMovementRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.repository.projection.CouponActivityRow;
import com.couponnumbergenerator.repository.projection.StatusFlowRow;
import com.couponnumbergenerator.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final CouponRepository couponRepository;
    private final CouponMovementRepository couponMovementRepository;
    private final LocationRepository locationRepository;
    private final FuelTypeRepository fuelTypeRepository;

    private static final int MAX_TREND_DAYS = 366;

    @Override
    @Transactional(readOnly = true)
    public List<InventorySummaryResponse> summarize(Long locationId, Long fuelTypeId, CouponStatus status) {
        validateLocation(locationId);
        return couponRepository.summarizeInventory(locationId, fuelTypeId, status).stream()
                .map(InventorySummaryResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CouponActivityResponse activity(LocalDate dateFrom, LocalDate dateTo, CouponStatus status,
                                           Long locationId, Long fuelTypeId) {
        validateRange(dateFrom, dateTo);
        validateLocation(locationId);
        validateFuelType(fuelTypeId);
        List<CouponActivityRow> rows = couponMovementRepository.summarizeActivity(
                dateFrom.atStartOfDay(), dateTo.plusDays(1).atStartOfDay(), status, locationId, fuelTypeId);
        return new CouponActivityResponse(dateFrom, dateTo, status, locationId, fuelTypeId, groupByStatus(rows));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponActivityResponse balance(LocalDate asAt, CouponStatus status, Long locationId, Long fuelTypeId) {
        validateLocation(locationId);
        validateFuelType(fuelTypeId);
        List<CouponActivityRow> rows = couponMovementRepository.summarizeBalance(
                asAt.plusDays(1).atStartOfDay(), status, locationId, fuelTypeId);
        return new CouponActivityResponse(asAt, asAt, status, locationId, fuelTypeId, groupByStatus(rows));
    }

    /** Opening balance just before dateFrom, then rolled forward one day at a time by that day's status changes. */
    @Override
    @Transactional(readOnly = true)
    public List<BalanceTrendPoint> balanceTrend(LocalDate dateFrom, LocalDate dateTo, Long fuelTypeId) {
        validateRange(dateFrom, dateTo);
        if (ChronoUnit.DAYS.between(dateFrom, dateTo) >= MAX_TREND_DAYS) {
            throw new IllegalArgumentException("A balance trend covers at most %d days".formatted(MAX_TREND_DAYS));
        }
        validateFuelType(fuelTypeId);

        Map<CouponStatus, BigDecimal> balance = new EnumMap<>(CouponStatus.class);
        for (CouponStatus s : CouponStatus.values()) {
            balance.put(s, BigDecimal.ZERO);
        }
        couponMovementRepository.summarizeBalance(dateFrom.atStartOfDay(), null, null, fuelTypeId)
                .forEach(row -> balance.merge(row.getStatus(), row.getLitres(), BigDecimal::add));

        Map<LocalDate, List<StatusFlowRow>> flowsByDay = new HashMap<>();
        couponMovementRepository.summarizeDailyFlows(dateFrom.atStartOfDay(), dateTo.plusDays(1).atStartOfDay(), fuelTypeId)
                .forEach(row -> flowsByDay.computeIfAbsent(row.getDay(), d -> new ArrayList<>()).add(row));

        List<BalanceTrendPoint> points = new ArrayList<>();
        for (LocalDate day = dateFrom; !day.isAfter(dateTo); day = day.plusDays(1)) {
            for (StatusFlowRow flow : flowsByDay.getOrDefault(day, List.of())) {
                if (flow.getFromStatus() != null) {
                    balance.merge(flow.getFromStatus(), flow.getLitres().negate(), BigDecimal::add);
                }
                balance.merge(flow.getToStatus(), flow.getLitres(), BigDecimal::add);
            }
            points.add(new BalanceTrendPoint(day, new EnumMap<>(balance)));
        }
        return points;
    }

    // TreeMap keeps statuses in lifecycle (enum) order, not the query's alphabetical one.
    private List<StatusSummary> groupByStatus(List<CouponActivityRow> rows) {
        Map<CouponStatus, List<CouponActivityRow>> rowsByStatus = new TreeMap<>();
        rows.forEach(row -> rowsByStatus.computeIfAbsent(row.getStatus(), s -> new ArrayList<>()).add(row));

        return rowsByStatus.entrySet().stream().map(entry -> {
            List<FuelTypeSummary> byFuelType = RedemptionReportServiceImpl.groupByFuelType(entry.getValue());
            return new StatusSummary(entry.getKey(),
                    byFuelType.stream().mapToLong(FuelTypeSummary::count).sum(),
                    byFuelType.stream().map(FuelTypeSummary::litres).reduce(BigDecimal.ZERO, BigDecimal::add),
                    byFuelType);
        }).toList();
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

    private void validateLocation(Long locationId) {
        if (locationId != null && !locationRepository.existsById(locationId)) {
            throw new LocationNotFoundException(locationId);
        }
    }
}
