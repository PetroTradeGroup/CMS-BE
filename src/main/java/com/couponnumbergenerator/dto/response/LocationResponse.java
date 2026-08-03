package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.LocationType;
import com.couponnumbergenerator.model.Location;

import java.time.LocalDateTime;

public record LocationResponse(
        Long id,
        String code,
        String name,
        LocationType type,
        String address,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LocationResponse from(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getCode(),
                location.getName(),
                location.getType(),
                location.getAddress(),
                location.isActive(),
                location.getCreatedAt(),
                location.getUpdatedAt()
        );
    }
}