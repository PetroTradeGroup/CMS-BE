package com.couponnumbergenerator.dto.response;

import java.time.LocalDateTime;

public record PageSizeLimitResponse(
        int maxPageSize,
        LocalDateTime updatedAt
) {}