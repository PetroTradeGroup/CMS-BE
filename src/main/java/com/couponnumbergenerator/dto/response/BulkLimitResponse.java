package com.couponnumbergenerator.dto.response;

import java.time.LocalDateTime;

public record BulkLimitResponse(
        int maxCount,
        LocalDateTime updatedAt
) {}