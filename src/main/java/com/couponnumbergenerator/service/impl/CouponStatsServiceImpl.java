package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.response.CouponStatsSnapshot;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.model.CouponSequence;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.CouponSequenceRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.projection.StatusCountRow;
import com.couponnumbergenerator.service.CouponStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponStatsServiceImpl implements CouponStatsService {

    private final CouponRepository couponRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final CouponSequenceRepository couponSequenceRepository;

    @Override
    @Transactional(readOnly = true)
    public CouponStatsSnapshot collect() {
        log.debug("Collecting coupon stats snapshot");

        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime lastWeekStart = weekStart.minusWeeks(1);
        LocalDateTime now = LocalDateTime.now();

        Map<CouponStatus, Long> byStatus = toStatusMap(couponRepository.countGroupedByStatus());
        long total    = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long thisWeek = couponRepository.countByCreatedAtBetween(weekStart, now);
        long lastWeek = couponRepository.countByCreatedAtBetween(lastWeekStart, weekStart);

        List<CouponStatsSnapshot.FuelTypeStats> breakdown = fuelTypeRepository.findAll()
                .stream()
                .map(this::toFuelTypeStats)
                .toList();

        List<CouponStatsSnapshot.SequenceCapacity> capacities = couponSequenceRepository.findAll()
                .stream()
                .map(this::toSequenceCapacity)
                .toList();

        return new CouponStatsSnapshot(total, byStatus, thisWeek, lastWeek,
                breakdown, capacities, LocalDate.now());
    }

    private CouponStatsSnapshot.FuelTypeStats toFuelTypeStats(FuelType fuelType) {
        Map<CouponStatus, Long> byStatus = toStatusMap(
                couponRepository.countByFuelTypeGroupedByStatus(fuelType.getId()));
        return new CouponStatsSnapshot.FuelTypeStats(
                fuelType.getName(),
                fuelType.getTypeCode(),
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus
        );
    }

    private Map<CouponStatus, Long> toStatusMap(List<StatusCountRow> rows) {
        Map<CouponStatus, Long> byStatus = new EnumMap<>(CouponStatus.class);
        rows.forEach(row -> byStatus.put(row.getStatus(), row.getCount()));
        return byStatus;
    }

    private CouponStatsSnapshot.SequenceCapacity toSequenceCapacity(CouponSequence seq) {
        double usedPct = (seq.getIssuedCount() * 100.0) / CouponConstants.MAX_COUPONS_PER_LETTER;
        return new CouponStatsSnapshot.SequenceCapacity(
                seq.getFuelType().getName(),
                String.valueOf(seq.getCurrentLetter()),
                seq.getIssuedCount(),
                Math.min(100.0, usedPct)
        );
    }
}