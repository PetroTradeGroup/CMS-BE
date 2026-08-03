package com.couponnumbergenerator.dto.response;

import java.time.LocalDateTime;

public record ValidityPeriodResponse(
        int defaultValidityDays,
        LocalDateTime updatedAt
) {}