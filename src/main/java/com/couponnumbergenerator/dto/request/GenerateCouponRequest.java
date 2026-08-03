package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GenerateCouponRequest(
        @NotNull(message = "Fuel type ID is required")
        Long fuelTypeId,

        @NotNull(message = "Denomination is required")
        @Positive(message = "Denomination must be greater than zero")
        BigDecimal denomination,

        Long locationId,

        Long departmentId,

        CouponType couponType,

        LocalDate expiryDate,

        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {}