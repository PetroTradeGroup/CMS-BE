package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Either {@code scannedPayloads} (signed QR content, verified against tampering) or
 * {@code couponNumbers} (manual fallback for a damaged QR) must be provided — at least one,
 * non-empty.
 */
public record RedemptionSubmitRequest(
        List<String> scannedPayloads,

        List<String> couponNumbers,

        @NotNull(message = "locationId is required — the site asserting the redemption")
        Long locationId,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason,

        @NotBlank(message = "Performed-by (the attendant) is required")
        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {}
