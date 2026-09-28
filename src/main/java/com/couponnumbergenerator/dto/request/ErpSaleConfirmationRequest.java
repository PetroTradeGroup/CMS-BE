package com.couponnumbergenerator.dto.request;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pushed back to BC once serials are assigned: BC's own document number, plus the assigned
 * serial range per line. Only assigned lines are included — a line that failed on stock has
 * no range to confirm.
 */
public record ErpSaleConfirmationRequest(
        String documentNumber,
        List<Line> lines
) {

    public record Line(
            int lineNumber,
            Long fuelTypeId,
            BigDecimal denomination,
            List<String> couponNumbers
    ) {}

    /** Total serials across all confirmed lines — for logging. */
    public int totalCouponNumbers() {
        return lines.stream().mapToInt(l -> l.couponNumbers().size()).sum();
    }
}
