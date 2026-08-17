package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GenerateBulkCouponRequest(
        @NotNull(message = "Fuel type ID is required")
        Long fuelTypeId,

        /** Optional cross-check: when set it must equal the lines' litres exactly; when omitted it is derived from them. */
        @Positive(message = "Target quantity must be greater than zero")
        BigDecimal targetQuantity,

        @NotEmpty(message = "At least one denomination line is required")
        @Valid
        List<DenominationLine> lines,

        Long locationId,

        Long departmentId,

        CouponType couponType,

        LocalDate expiryDate,

        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {
    public int totalCount() {
        return lines.stream().mapToInt(DenominationLine::resolvedCount).sum();
    }

    /** Sum of denomination * count across all lines — must equal {@link #targetQuantity()} exactly when that is set. */
    public BigDecimal breakdownTotal() {
        return lines.stream()
                .map(DenominationLine::resolvedLitres)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}