package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Buy fuel by amount of money: we work out the litres and issue one coupon for them. The amount
 * must buy a whole number of litres at the current price — otherwise 400, naming the nearest
 * amounts that do (GET /bank/quote gives the same options up front). Idempotent on
 * {@code bankReference}, shared with POST /bank/purchases.
 */
public record BankAmountPurchaseRequest(
        @NotBlank(message = "bankReference is required")
        @Size(max = 50, message = "bankReference must be at most 50 characters")
        String bankReference,

        @Size(max = 100, message = "customerReference must be at most 100 characters")
        String customerReference,

        @NotNull(message = "fuelTypeId is required")
        Long fuelTypeId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount
) {}
