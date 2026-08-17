package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.CouponStatus;
import jakarta.validation.constraints.Size;

/**
 * Auto-fulfill a requisition: no batch or lines are named — the system plans whole books
 * against the outstanding lines itself, drawing from the oldest batches of each line's fuel
 * type first (FIFO) and spilling into the next batch when one runs dry.
 */
public record AutoFulfillRequisitionRequest(
        CouponStatus targetStatus,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason,

        @Size(max = 100, message = "Performed-by must be at most 100 characters")
        String performedBy
) {}