package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Registers a pre-existing coupon (e.g. an old barcoded coupon predating this system) directly
 * at ALLOCATED, skipping generation/receipt — for a coupon already out with a customer that
 * needs to become redeemable through the normal {@code /redemptions} flow.
 */
public record ImportLegacyCouponRequest(
        @NotBlank(message = "couponNumber is required")
        @Size(max = 20, message = "couponNumber must be at most 20 characters")
        String couponNumber,

        @NotNull(message = "Fuel type ID is required")
        Long fuelTypeId,

        @NotNull(message = "Denomination is required")
        @Positive(message = "Denomination must be greater than zero")
        BigDecimal denomination,

        Long locationId,

        Long departmentId,

        LocalDate expiryDate,

        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {}
