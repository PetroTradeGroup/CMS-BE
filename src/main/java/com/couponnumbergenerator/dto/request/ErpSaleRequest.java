package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * A sale event pushed by Business Central's outbox worker. {@code documentNumber} is the
 * idempotency key — redelivering the same document number is always safe, a no-op that
 * returns the sale's existing state.
 */
public record ErpSaleRequest(
        @NotBlank(message = "documentNumber is required")
        @Size(max = 50, message = "documentNumber must be at most 50 characters")
        String documentNumber,

        @NotBlank(message = "locationCode is required — the branch that made the sale")
        String locationCode,

        @NotNull(message = "fuelTypeId is required")
        Long fuelTypeId,

        @NotNull(message = "denomination is required")
        @Positive(message = "denomination must be greater than zero")
        BigDecimal denomination,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than zero")
        Integer quantity,

        @Size(max = 100, message = "customerReference must be at most 100 characters")
        String customerReference
) {}