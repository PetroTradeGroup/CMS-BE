package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateBulkLimitRequest(
        @NotNull(message = "Max count is required")
        @Min(value = 1, message = "Max count must be at least 1")
        Integer maxCount
) {}