package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.model.DenominationCount;

import java.math.BigDecimal;

/** How many coupons of one denomination an {@link ApprovalRequestResponse} covers. */
public record DenominationCountResponse(
        BigDecimal denomination,
        int count
) {
    public static DenominationCountResponse from(DenominationCount denominationCount) {
        return new DenominationCountResponse(denominationCount.getDenomination(), denominationCount.getCount());
    }
}