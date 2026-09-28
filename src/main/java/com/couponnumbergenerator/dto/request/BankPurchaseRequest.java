package com.couponnumbergenerator.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * A customer's virtual-coupon purchase, posted by the bank after it has taken payment.
 * {@code bankReference} is the idempotency key: re-posting it returns the original purchase.
 * {@code amount} must equal litres × the fuel type's current price per litre, so the bank can't
 * charge a stale price without us noticing.
 */
public record BankPurchaseRequest(
        @NotBlank(message = "bankReference is required")
        @Size(max = 50, message = "bankReference must be at most 50 characters")
        String bankReference,

        @Size(max = 100, message = "customerReference must be at most 100 characters")
        String customerReference,

        @NotNull(message = "fuelTypeId is required")
        Long fuelTypeId,

        /** Coupons to mint, one line per denomination — use {@code count}, not {@code books}. */
        @NotEmpty(message = "at least one line is required")
        @Valid
        List<DenominationLine> lines,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        BigDecimal amount
) {}
