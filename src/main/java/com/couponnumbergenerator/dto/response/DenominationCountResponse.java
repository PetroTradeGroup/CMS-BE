package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.model.DenominationCount;

import java.math.BigDecimal;

/**
 * How many coupons of one denomination an {@link ApprovalRequestResponse} covers — with the
 * book and litre equivalents, so the receiving side can tally physical books against litres.
 * {@code books} is null when the count doesn't divide into whole books (legacy quantities).
 */
public record DenominationCountResponse(
        BigDecimal denomination,
        int count,
        Integer books,
        BigDecimal litres
) {
    /** Derives {@code books} and {@code litres} from the denomination and count. */
    public DenominationCountResponse(BigDecimal denomination, int count) {
        this(denomination, count,
                count % CouponConstants.BOOK_SIZE == 0 ? count / CouponConstants.BOOK_SIZE : null,
                denomination.multiply(BigDecimal.valueOf(count)));
    }

    public static DenominationCountResponse from(DenominationCount denominationCount) {
        return new DenominationCountResponse(denominationCount.getDenomination(), denominationCount.getCount());
    }
}