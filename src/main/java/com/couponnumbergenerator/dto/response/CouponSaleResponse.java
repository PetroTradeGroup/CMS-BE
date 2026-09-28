package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.model.CouponSale;

import java.time.LocalDateTime;
import java.util.List;

public record CouponSaleResponse(
        Long id,
        String bcDocumentNumber,
        LocationResponse location,
        SaleStatus status,
        List<CouponSaleLineResponse> lines,
        String customerReference,
        LocalDateTime receivedAt,
        LocalDateTime assignedAt,
        LocalDateTime pushedAt
) {
    public static CouponSaleResponse from(CouponSale sale) {
        return new CouponSaleResponse(
                sale.getId(),
                sale.getBcDocumentNumber(),
                LocationResponse.from(sale.getLocation()),
                sale.getStatus(),
                sale.getLines().stream().map(CouponSaleLineResponse::from).toList(),
                sale.getCustomerReference(),
                sale.getReceivedAt(),
                sale.getAssignedAt(),
                sale.getPushedAt()
        );
    }
}
