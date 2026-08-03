package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.Size;

public record ReceiveBatchRequest(
        Long locationId,

        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {
    public static ReceiveBatchRequest empty() {
        return new ReceiveBatchRequest(null, null);
    }
}