package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TransitionRequest(
        @NotEmpty(message = "At least one coupon number is required")
        List<String> couponNumbers,

        @NotNull(message = "Target status is required")
        CouponStatus targetStatus,

        Long toLocationId,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason,

        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {}