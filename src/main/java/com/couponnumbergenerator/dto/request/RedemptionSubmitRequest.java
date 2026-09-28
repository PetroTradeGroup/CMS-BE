package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * At least one of {@code scannedPayloads} (signed QR content, verified against tampering),
 * {@code couponNumbers} (manual fallback for a damaged QR — physical coupons only) or
 * {@code redemptionCodes} (the 7-character code a virtual coupon's holder reads out; case, spaces
 * and hyphens ignored) must be non-empty. They can be mixed in one submission.
 *
 * <p>There's no location or performed-by field: only station staff redeem, and the station and
 * attendant always come from their token (see {@code LocationAccessGuard}). Older clients that
 * still send {@code locationId}/{@code performedBy} have them ignored.
 *
 * <p>{@code carRegistrationNumber} is always required — the attendant must record which vehicle a
 * redemption was for.
 */
public record RedemptionSubmitRequest(
        List<String> scannedPayloads,

        List<String> couponNumbers,

        List<String> redemptionCodes,

        @NotBlank(message = "carRegistrationNumber is required")
        @Size(max = 20, message = "carRegistrationNumber must be at most 20 characters")
        String carRegistrationNumber,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason
) {}
