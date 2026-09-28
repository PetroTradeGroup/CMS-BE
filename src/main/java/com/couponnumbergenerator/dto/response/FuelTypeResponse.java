package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.model.FuelType;

import java.math.BigDecimal;

public record FuelTypeResponse(
        Long id,
        String name,
        String typeCode,
        String description,
        boolean active,
        BigDecimal pricePerLitre
) {
    public static FuelTypeResponse from(FuelType fuelType) {
        return new FuelTypeResponse(
                fuelType.getId(),
                fuelType.getName(),
                fuelType.getTypeCode(),
                fuelType.getDescription(),
                fuelType.isActive(),
                fuelType.getPricePerLitre()
        );
    }
}
