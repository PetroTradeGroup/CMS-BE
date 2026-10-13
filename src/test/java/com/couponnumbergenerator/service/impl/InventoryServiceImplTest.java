package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.response.BalanceTrendPoint;
import com.couponnumbergenerator.dto.response.CouponActivityResponse;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.repository.CouponMovementRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.repository.projection.CouponActivityRow;
import com.couponnumbergenerator.repository.projection.StatusFlowRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryServiceImplTest {

    private final CouponMovementRepository movementRepository = mock(CouponMovementRepository.class);
    private final InventoryServiceImpl service = new InventoryServiceImpl(mock(CouponRepository.class),
            movementRepository, mock(LocationRepository.class), mock(FuelTypeRepository.class));

    @Test
    void activityGroupsStatusThenFuelTypeWithTotals() {
        // Query orders statuses alphabetically (REDEEMED before IN_STOCK); the report uses lifecycle order.
        List<CouponActivityRow> rows = List.of(
                row(CouponStatus.REDEEMED, 1L, "Diesel", 20, 3),
                row(CouponStatus.REDEEMED, 1L, "Diesel", 50, 2),
                row(CouponStatus.REDEEMED, 2L, "Petrol", 20, 1),
                row(CouponStatus.IN_STOCK, 1L, "Diesel", 20, 100));
        when(movementRepository.summarizeActivity(any(), any(), any(), any(), any())).thenReturn(rows);

        CouponActivityResponse result = service.activity(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null, null);

        assertThat(result.byStatus()).extracting(CouponActivityResponse.StatusSummary::status)
                .containsExactly(CouponStatus.IN_STOCK, CouponStatus.REDEEMED);
        CouponActivityResponse.StatusSummary redeemed = result.byStatus().get(1);
        assertThat(redeemed.count()).isEqualTo(6);
        assertThat(redeemed.litres()).isEqualByComparingTo("180");
        assertThat(redeemed.byFuelType()).hasSize(2);
        assertThat(redeemed.byFuelType().getFirst().litres()).isEqualByComparingTo("160");
    }

    @Test
    void balanceTrendRollsTheOpeningBalanceForwardByEachDaysFlows() {
        LocalDate day1 = LocalDate.of(2026, 9, 1), day2 = day1.plusDays(1), day3 = day1.plusDays(2);
        List<CouponActivityRow> opening = List.of(row(CouponStatus.IN_STOCK, 1L, "Diesel", 20, 50)); // 1000 L
        List<StatusFlowRow> flows = List.of(
                flow(day1, CouponStatus.IN_STOCK, CouponStatus.ALLOCATED, 300),   // sold
                flow(day3, CouponStatus.ALLOCATED, CouponStatus.REDEEMED, 100),   // redeemed
                flow(day3, null, CouponStatus.GENERATED, 500));                   // new batch
        when(movementRepository.summarizeBalance(any(), any(), any(), any())).thenReturn(opening);
        when(movementRepository.summarizeDailyFlows(any(), any(), any())).thenReturn(flows);

        List<BalanceTrendPoint> trend = service.balanceTrend(day1, day3, null);

        assertThat(trend).extracting(BalanceTrendPoint::date).containsExactly(day1, day2, day3);
        assertThat(trend.get(0).litres().get(CouponStatus.IN_STOCK)).isEqualByComparingTo("700");
        assertThat(trend.get(1).litres().get(CouponStatus.ALLOCATED)).isEqualByComparingTo("300"); // quiet day carries over
        assertThat(trend.get(2).litres().get(CouponStatus.ALLOCATED)).isEqualByComparingTo("200");
        assertThat(trend.get(2).litres().get(CouponStatus.REDEEMED)).isEqualByComparingTo("100");
        assertThat(trend.get(2).litres().get(CouponStatus.GENERATED)).isEqualByComparingTo("500");
    }

    private StatusFlowRow flow(LocalDate day, CouponStatus from, CouponStatus to, int litres) {
        StatusFlowRow row = mock(StatusFlowRow.class);
        when(row.getDay()).thenReturn(day);
        when(row.getFromStatus()).thenReturn(from);
        when(row.getToStatus()).thenReturn(to);
        when(row.getLitres()).thenReturn(BigDecimal.valueOf(litres));
        return row;
    }

    private CouponActivityRow row(CouponStatus status, Long fuelTypeId, String fuelName, int denomination, long count) {
        CouponActivityRow row = mock(CouponActivityRow.class);
        when(row.getStatus()).thenReturn(status);
        when(row.getFuelTypeId()).thenReturn(fuelTypeId);
        when(row.getFuelTypeName()).thenReturn(fuelName);
        when(row.getDenomination()).thenReturn(BigDecimal.valueOf(denomination));
        when(row.getCount()).thenReturn(count);
        when(row.getLitres()).thenReturn(BigDecimal.valueOf((long) denomination * count));
        return row;
    }
}
