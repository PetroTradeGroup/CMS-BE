package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.model.RequisitionLine;

import java.math.BigDecimal;

/**
 * One line of a requisition, in both units: litres (stored) and physical books (derived —
 * litres ÷ (denomination × {@link CouponConstants#BOOK_SIZE}); null when the litres don't
 * form whole books, e.g. on legacy lines).
 */
public record RequisitionLineResponse(
        FuelTypeResponse fuelType,
        BigDecimal denomination,
        BigDecimal requestedLitres,
        BigDecimal fulfilledLitres,
        BigDecimal outstandingLitres,
        Integer requestedBooks,
        Integer fulfilledBooks,
        Integer outstandingBooks
) {
    public static RequisitionLineResponse from(RequisitionLine line) {
        return new RequisitionLineResponse(FuelTypeResponse.from(line.getFuelType()),
                line.getDenomination(), line.getRequestedLitres(), line.getFulfilledLitres(), line.outstandingLitres(),
                wholeBooks(line.getRequestedLitres(), line.getDenomination()),
                wholeBooks(line.getFulfilledLitres(), line.getDenomination()),
                wholeBooks(line.outstandingLitres(), line.getDenomination()));
    }

    private static Integer wholeBooks(BigDecimal litres, BigDecimal denomination) {
        BigDecimal bookLitres = denomination.multiply(BigDecimal.valueOf(CouponConstants.BOOK_SIZE));
        BigDecimal[] booksAndRemainder = litres.divideAndRemainder(bookLitres);
        return booksAndRemainder[1].compareTo(BigDecimal.ZERO) == 0
                ? booksAndRemainder[0].intValueExact()
                : null;
    }
}