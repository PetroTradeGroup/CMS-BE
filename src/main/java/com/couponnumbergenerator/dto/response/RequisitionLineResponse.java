package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.model.RequisitionLine;

import java.math.BigDecimal;

public record RequisitionLineResponse(
        FuelTypeResponse fuelType,
        BigDecimal denomination,
        BigDecimal requestedLitres,
        BigDecimal fulfilledLitres,
        BigDecimal outstandingLitres
) {
    public static RequisitionLineResponse from(RequisitionLine line) {
        return new RequisitionLineResponse(FuelTypeResponse.from(line.getFuelType()),
                line.getDenomination(), line.getRequestedLitres(), line.getFulfilledLitres(), line.outstandingLitres());
    }
}