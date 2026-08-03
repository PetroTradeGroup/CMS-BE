package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Moves coupons of a batch to a new location and/or department, optionally with a status change.
 * Coupons are selected one of three ways (mutually exclusive):
 * <ul>
 *   <li>whole batch — omit {@code rangeStart}/{@code rangeEnd} and {@code denominationLines};</li>
 *   <li>position range — 1-indexed inclusive {@code rangeStart}..{@code rangeEnd} (both-or-neither);</li>
 *   <li>denomination pick — for each {@link DenominationLine}, the first {@code count} coupons of that
 *       denomination (batch order) that are currently eligible for the requested move; an ineligible
 *       coupon is skipped in favor of the next eligible one. One call can mix denominations, e.g.
 *       5 x 20L + 3 x 50L in a single transfer.</li>
 * </ul>
 * A range/whole-batch selection takes positions exactly as given, so one ineligible coupon in it
 * rejects the whole request; a denomination pick instead fails only if it can't find enough eligible
 * coupons of a line's denomination.
 */
public record TransferRequest(
        @NotNull(message = "Batch ID is required")
        Long batchId,

        Integer rangeStart,

        Integer rangeEnd,

        @Valid
        List<DenominationLine> denominationLines,

        Long toLocationId,

        Long toDepartmentId,

        CouponStatus targetStatus,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason,

        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {}