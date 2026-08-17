package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.constants.CouponConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * One line of a requisition or a fulfillment against one: a quantity of a given fuel type at a
 * given coupon denomination, expressed either as physical {@code books}
 * ({@link CouponConstants#BOOK_SIZE} coupons each — the unit Stocks think in, e.g. "200 books of
 * 20 L petrol") or as raw {@code litres}. Exactly one of the two must be set; litres are derived
 * from books as {@code books × 100 × denomination}.
 */
public record RequisitionLineRequest(
        @NotNull(message = "Fuel type is required")
        Long fuelTypeId,

        @NotNull(message = "Denomination is required")
        @Positive(message = "Denomination must be greater than zero")
        BigDecimal denomination,

        @Positive(message = "Litres must be greater than zero")
        BigDecimal litres,

        @Positive(message = "Books must be greater than zero")
        Integer books
) {

    public RequisitionLineRequest(Long fuelTypeId, BigDecimal denomination, BigDecimal litres) {
        this(fuelTypeId, denomination, litres, null);
    }

    /** Litres of one whole book at this line's denomination. */
    public BigDecimal bookLitres() {
        return denomination.multiply(BigDecimal.valueOf(CouponConstants.BOOK_SIZE));
    }

    /** The litres this line resolves to, whichever way it was expressed. */
    public BigDecimal resolvedLitres() {
        return books != null ? bookLitres().multiply(BigDecimal.valueOf(books)) : litres;
    }

    /** Exactly one of {@code litres}/{@code books} must be provided; call before trusting {@link #resolvedLitres()}. */
    public void validateQuantity() {
        if ((litres == null) == (books == null)) {
            throw new IllegalArgumentException(
                    "Line %s L: provide exactly one of 'litres' or 'books'"
                            .formatted(denomination.toPlainString()));
        }
    }
}