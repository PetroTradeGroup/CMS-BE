package com.couponnumbergenerator.dto.response;

import java.time.LocalDateTime;

public record InsightResponse(
        String summary,
        CouponStatsSnapshot stats,
        LocalDateTime generatedAt
) {}