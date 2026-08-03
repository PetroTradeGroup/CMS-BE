package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFuelTypeRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 20, message = "Name must be at most 20 characters")
        String name,

        @NotBlank(message = "Type code is required")
        @Size(min = 3, max = 5, message = "Type code must be 3–5 characters")
        String typeCode,

        String description
) {}