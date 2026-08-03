package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.response.InventorySummaryResponse;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final CouponRepository couponRepository;
    private final LocationRepository locationRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InventorySummaryResponse> summarize(Long locationId, Long fuelTypeId, CouponStatus status) {
        if (locationId != null && !locationRepository.existsById(locationId)) {
            throw new LocationNotFoundException(locationId);
        }
        return couponRepository.summarizeInventory(locationId, fuelTypeId, status).stream()
                .map(InventorySummaryResponse::from)
                .toList();
    }
}