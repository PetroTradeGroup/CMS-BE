package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.dto.request.DenominationLine;
import com.couponnumbergenerator.enums.CouponStatus;

import java.util.List;

public record TransferResultResponse(
        int count,
        Long batchId,
        String batchNumber,
        Integer rangeStart,
        Integer rangeEnd,
        List<DenominationLine> denominationLines,
        List<TransferredCouponResponse> coupons,
        CouponStatus targetStatus
) {}
