package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.model.Department;

import java.time.LocalDateTime;

public record DepartmentResponse(
        Long id,
        String code,
        String name,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getCode(),
                department.getName(),
                department.isActive(),
                department.getCreatedAt(),
                department.getUpdatedAt()
        );
    }
}
