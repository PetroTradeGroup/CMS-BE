package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdatePageSizeLimitRequest(
        @NotNull(message = "Max page size is required")
        @Min(value = 1, message = "Max page size must be at least 1")
        Integer maxPageSize
) {}