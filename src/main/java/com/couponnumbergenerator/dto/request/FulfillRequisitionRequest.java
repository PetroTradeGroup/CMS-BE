package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Stock's response to a requisition: what's being issued right now, which may be less than
 * what was originally requested (a partial delivery/drawdown) — the outstanding balance per
 * line stays open on the requisition for a later fulfillment.
 */
public record FulfillRequisitionRequest(
        @NotNull(message = "Batch ID is required")
        Long batchId,

        @NotEmpty(message = "At least one line is required")
        @Valid
        List<RequisitionLineRequest> lines,

        CouponStatus targetStatus,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason,

        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {}