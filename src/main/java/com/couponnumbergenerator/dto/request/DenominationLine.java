package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DenominationLine(
        @NotNull(message = "Denomination is required")
        @Positive(message = "Denomination must be greater than zero")
        BigDecimal denomination,

        @Min(value = 1, message = "Count must be at least 1")
        int count
) {}