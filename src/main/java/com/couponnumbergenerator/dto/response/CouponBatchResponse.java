package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.model.CouponBatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record CouponBatchResponse(
        Long id,
        String batchNumber,
        Long sequenceNumber,
        FuelTypeResponse fuelType,
        CouponType couponType,
        int quantity,
        BigDecimal targetQuantity,
        LocationResponse originLocation,
        LocalDate expiryDate,
        String createdBy,
        LocalDateTime createdAt,
        Map<CouponStatus, Long> statusCounts
) {
    public static CouponBatchResponse from(CouponBatch batch) {
        return from(batch, null);
    }

    public static CouponBatchResponse from(CouponBatch batch, Map<CouponStatus, Long> statusCounts) {
        return new CouponBatchResponse(
                batch.getId(),
                batch.getBatchNumber(),
                batch.getSequenceNumber(),
                FuelTypeResponse.from(batch.getFuelType()),
                batch.getCouponType(),
                batch.getQuantity(),
                batch.getTargetQuantity(),
                LocationResponse.from(batch.getOriginLocation()),
                batch.getExpiryDate(),
                batch.getCreatedBy(),
                batch.getCreatedAt(),
                statusCounts
        );
    }
}