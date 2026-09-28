package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Buy a whole number of litres as one coupon; we work out the amount to charge (litres × current
 * price) and return it. Idempotent on {@code bankReference}, shared with the other purchase endpoints.
 */
public record BankLitresPurchaseRequest(
        @NotBlank(message = "bankReference is required")
        @Size(max = 50, message = "bankReference must be at most 50 characters")
        String bankReference,

        @Size(max = 100, message = "customerReference must be at most 100 characters")
        String customerReference,

        @NotNull(message = "fuelTypeId is required")
        Long fuelTypeId,

        @NotNull(message = "litres is required")
        @Positive(message = "litres must be a whole number greater than zero")
        Integer litres
) {}
