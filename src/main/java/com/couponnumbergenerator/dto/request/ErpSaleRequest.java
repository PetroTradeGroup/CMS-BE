package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.constants.CouponConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * A sale event pushed by Business Central's outbox worker, one per BC sales document.
 * {@code documentNumber} is the idempotency key — redelivering the same document number is
 * always safe, a no-op that returns the sale's existing state. The document carries one or
 * more {@link Line}s, one per fuel type + denomination (§11.6 of
 * {@code docs/erp-sales-integration-design.md}).
 */
public record ErpSaleRequest(
        @NotBlank(message = "documentNumber is required")
        @Size(max = 50, message = "documentNumber must be at most 50 characters")
        String documentNumber,

        @NotBlank(message = "locationCode is required — the branch that made the sale")
        String locationCode,

        @NotEmpty(message = "at least one line is required")
        @Valid
        List<Line> lines,

        @Size(max = 100, message = "customerReference must be at most 100 characters")
        String customerReference
) {

    /**
     * One fuel type + denomination within the sales document, with the amount sold expressed
     * either as a loose coupon {@code quantity} or as a number of physical {@code books}
     * ({@link CouponConstants#BOOK_SIZE} coupons each) — exactly one of the two. Stocks and BC
     * both think in books ("1.5 books of 20 L" = 150 coupons), so {@code books} is the preferred
     * form; it may be fractional as long as it resolves to a whole number of coupons.
     * {@code quantity} remains for callers that still count loose coupons.
     */
    public record Line(
            @NotNull(message = "fuelTypeId is required")
            Long fuelTypeId,

            @NotNull(message = "denomination is required")
            @Positive(message = "denomination must be greater than zero")
            BigDecimal denomination,

            @Positive(message = "quantity must be greater than zero")
            Integer quantity,

            @Positive(message = "books must be greater than zero")
            BigDecimal books,

            /** True when BC sells this line as whole intact books rather than a loose count. */
            Boolean wholeBooks
    ) {
        public boolean wholeBooksOrFalse() {
            return Boolean.TRUE.equals(wholeBooks);
        }

        /**
         * Exactly one of {@code quantity}/{@code books} must be set, and a {@code books} value
         * must resolve to a whole number of coupons (e.g. 1.5 books → 150, but 1.005 books is
         * rejected). Call before trusting {@link #resolvedQuantity()}.
         */
        public void validateQuantity() {
            if ((quantity == null) == (books == null)) {
                throw new IllegalArgumentException(
                        "Sale line for %s L: provide exactly one of 'quantity' or 'books'"
                                .formatted(denomination.toPlainString()));
            }
            if (books != null && bookCoupons().stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException(
                        "Sale line for %s L: 'books' (%s) must resolve to a whole number of coupons"
                                .formatted(denomination.toPlainString(), books.toPlainString()));
            }
        }

        /** The loose coupon count this line resolves to, whichever way it was expressed. */
        public int resolvedQuantity() {
            return books != null ? bookCoupons().intValueExact() : quantity;
        }

        private BigDecimal bookCoupons() {
            return books.multiply(BigDecimal.valueOf(CouponConstants.BOOK_SIZE));
        }
    }
}
