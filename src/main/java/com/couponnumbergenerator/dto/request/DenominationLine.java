package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.constants.CouponConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * A quantity of coupons at one denomination, expressed either as a coupon {@code count} or as a
 * number of physical {@code books} ({@link CouponConstants#BOOK_SIZE} coupons each) — exactly one
 * of the two must be set. Stocks think in books ("200 books of 20 L"), so {@code books} is the
 * preferred form; {@code count} remains for digital coupons and legacy callers.
 */
public record DenominationLine(
        @NotNull(message = "Denomination is required")
        @Positive(message = "Denomination must be greater than zero")
        BigDecimal denomination,

        @Positive(message = "Count must be greater than zero")
        Integer count,

        @Positive(message = "Books must be greater than zero")
        Integer books
) {

    public DenominationLine(BigDecimal denomination, int count) {
        this(denomination, count, null);
    }

    /** The coupon count this line resolves to, whichever way it was expressed. */
    public int resolvedCount() {
        return books != null ? books * CouponConstants.BOOK_SIZE : count;
    }

    /** Litres this line resolves to: denomination × resolved count. */
    public BigDecimal resolvedLitres() {
        return denomination.multiply(BigDecimal.valueOf(resolvedCount()));
    }

    /** Exactly one of {@code count}/{@code books} must be provided; call before trusting {@link #resolvedCount()}. */
    public void validateQuantity() {
        if ((count == null) == (books == null)) {
            throw new IllegalArgumentException(
                    "Denomination line %s L: provide exactly one of 'count' or 'books'"
                            .formatted(denomination.toPlainString()));
        }
    }
}