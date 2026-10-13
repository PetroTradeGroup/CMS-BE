package com.couponnumbergenerator.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * {@code fromBook}/{@code toBook} (inclusive) receive only part of a batch — a supplier delivering
 * 100 of 200 books now and the rest later. Both null receives everything still awaiting receipt.
 */
public record ReceiveBatchRequest(
        Long locationId,

        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy,

        @Positive(message = "From-book must be positive")
        Integer fromBook,

        @Positive(message = "To-book must be positive")
        Integer toBook
) {
    public ReceiveBatchRequest(Long locationId, String performedBy) {
        this(locationId, performedBy, null, null);
    }

    public static ReceiveBatchRequest empty() {
        return new ReceiveBatchRequest(null, null);
    }

    public boolean isPartial() {
        return fromBook != null;
    }

    @AssertTrue(message = "fromBook and toBook must be given together, with fromBook <= toBook")
    private boolean isBookRangeValid() {
        return (fromBook == null && toBook == null)
                || (fromBook != null && toBook != null && fromBook <= toBook);
    }
}
