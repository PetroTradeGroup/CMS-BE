package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateValidityPeriodRequest(
        @NotNull(message = "Default validity days is required")
        @Min(value = 1, message = "Default validity must be at least 1 day")
        @Max(value = 3650, message = "Default validity must be at most 3650 days (10 years)")
        Integer defaultValidityDays
) {}