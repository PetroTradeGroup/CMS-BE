package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReceiptConfirmationRequest(
        @NotBlank(message = "Received-by is required")
        @Size(max = 100, message = "Received-by must be at most 100 characters")
        String receivedBy,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {}