package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.NotBlank;

/** The raw content of a scanned coupon QR code (the HMAC-signed payload). */
public record ScanRequest(
        @NotBlank(message = "payload is required — the scanned QR content")
        String payload
) {}