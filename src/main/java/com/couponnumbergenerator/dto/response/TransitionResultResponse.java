package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponStatus;

public record TransitionResultResponse(
        int count,
        CouponStatus targetStatus
) {}