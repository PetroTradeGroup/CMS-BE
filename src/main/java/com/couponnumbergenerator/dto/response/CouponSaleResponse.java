package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.model.CouponSale;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CouponSaleResponse(
        Long id,
        String bcDocumentNumber,
        LocationResponse location,
        FuelTypeResponse fuelType,
        BigDecimal denomination,
        int requestedCount,
        SaleStatus status,
        List<String> couponNumbers,
        String customerReference,
        String failureReason,
        LocalDateTime receivedAt,
        LocalDateTime assignedAt,
        LocalDateTime pushedAt
) {
    public static CouponSaleResponse from(CouponSale sale) {
        return new CouponSaleResponse(
                sale.getId(),
                sale.getBcDocumentNumber(),
                LocationResponse.from(sale.getLocation()),
                FuelTypeResponse.from(sale.getFuelType()),
                sale.getDenomination(),
                sale.getRequestedCount(),
                sale.getStatus(),
                sale.getCouponNumbers(),
                sale.getCustomerReference(),
                sale.getFailureReason(),
                sale.getReceivedAt(),
                sale.getAssignedAt(),
                sale.getPushedAt()
        );
    }
}