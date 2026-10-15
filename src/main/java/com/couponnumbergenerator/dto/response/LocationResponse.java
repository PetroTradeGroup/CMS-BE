package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.LocationType;
import com.couponnumbergenerator.model.Company;
import com.couponnumbergenerator.model.Location;

import java.time.LocalDateTime;

public record LocationResponse(
        Long id,
        String code,
        String name,
        LocationType type,
        String address,
        Long companyId,
        String companyCode,
        String companyName,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LocationResponse from(Location location) {
        Company company = location.getCompany(); // never null in the DB (V31), may be in hand-built test fixtures
        return new LocationResponse(
                location.getId(),
                location.getCode(),
                location.getName(),
                location.getType(),
                location.getAddress(),
                company == null ? null : company.getId(),
                company == null ? null : company.getCode(),
                company == null ? null : company.getName(),
                location.isActive(),
                location.getCreatedAt(),
                location.getUpdatedAt()
        );
    }
}