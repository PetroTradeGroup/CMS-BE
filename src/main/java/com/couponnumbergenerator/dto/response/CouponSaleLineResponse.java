package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.SaleLineStatus;
import com.couponnumbergenerator.model.CouponSaleLine;

import java.math.BigDecimal;
import java.util.List;

public record CouponSaleLineResponse(
        Long id,
        int lineNumber,
        FuelTypeResponse fuelType,
        BigDecimal denomination,
        int requestedCount,
        boolean wholeBooks,
        SaleLineStatus status,
        String failureReason,
        List<String> couponNumbers
) {
    public static CouponSaleLineResponse from(CouponSaleLine line) {
        return new CouponSaleLineResponse(
                line.getId(),
                line.getLineNumber(),
                FuelTypeResponse.from(line.getFuelType()),
                line.getDenomination(),
                line.getRequestedCount(),
                line.isWholeBooks(),
                line.getStatus(),
                line.getFailureReason(),
                line.getCouponNumbers()
        );
    }
}
