package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedemptionPostRequest(
        @NotBlank(message = "documentNumber is required")
        @Size(max = 50, message = "documentNumber must be at most 50 characters")
        String documentNumber,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason,

        @NotBlank(message = "Performed-by is required")
        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {}
