package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.LocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLocationRequest(
        @NotBlank(message = "Code is required")
        @Size(max = 10, message = "Code must be at most 10 characters")
        String code,

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @NotNull(message = "Location type is required")
        LocationType type,

        @Size(max = 255, message = "Address must be at most 255 characters")
        String address
) {}