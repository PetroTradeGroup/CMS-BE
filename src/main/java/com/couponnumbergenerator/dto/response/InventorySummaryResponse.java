package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.repository.projection.InventorySummaryRow;

public record InventorySummaryResponse(
        Long locationId,
        String locationCode,
        String locationName,
        Long fuelTypeId,
        String fuelTypeName,
        CouponStatus status,
        long count
) {
    public static InventorySummaryResponse from(InventorySummaryRow row) {
        return new InventorySummaryResponse(
                row.getLocationId(),
                row.getLocationCode(),
                row.getLocationName(),
                row.getFuelTypeId(),
                row.getFuelTypeName(),
                row.getStatus(),
                row.getCount()
        );
    }
}