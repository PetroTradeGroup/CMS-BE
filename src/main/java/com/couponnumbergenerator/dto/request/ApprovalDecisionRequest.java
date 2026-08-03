package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApprovalDecisionRequest(
        @NotBlank(message = "Approved-by is required")
        @Size(max = 100, message = "Approved-by must be at most 100 characters")
        String approvedBy,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {}
