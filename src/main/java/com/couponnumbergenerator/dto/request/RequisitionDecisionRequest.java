package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisitionDecisionRequest(
        @NotBlank(message = "Decided-by is required")
        @Size(max = 100, message = "Decided-by must be at most 100 characters")
        String decidedBy,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {}