package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.response.InventorySummaryResponse;
import com.couponnumbergenerator.enums.CouponStatus;

import java.util.List;

public interface InventoryService {

    /** Stock-on-hand grouped by location, fuel type and status; each filter is optional. */
    List<InventorySummaryResponse> summarize(Long locationId, Long fuelTypeId, CouponStatus status);
}