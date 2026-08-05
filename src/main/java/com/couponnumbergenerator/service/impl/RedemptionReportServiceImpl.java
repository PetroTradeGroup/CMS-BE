package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.response.RedemptionDailySummaryResponse;
import com.couponnumbergenerator.dto.response.RedemptionDailySummaryResponse.DenominationLine;
import com.couponnumbergenerator.dto.response.RedemptionDailySummaryResponse.FuelTypeSummary;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.repository.CouponMovementRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.repository.projection.RedemptionSummaryRow;
import com.couponnumbergenerator.service.RedemptionReportService;
import lombok.RequiredArgsConstructor;
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

    @Override
    @Transactional(readOnly = true)
    public RedemptionDailySummaryResponse dailySummary(LocalDate date, Long locationId) {
        if (locationId != null && !locationRepository.existsById(locationId)) {
            throw new LocationNotFoundException(locationId);
        }
        List<RedemptionSummaryRow> rows = couponMovementRepository.summarizeRedemptions(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay(), locationId);

        // Rows arrive ordered by fuel type then denomination, so each fuel type's lines are contiguous.
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
        return new RedemptionDailySummaryResponse(date, locationId, totalCoupons, totalLitres, byFuelType);
    }
}