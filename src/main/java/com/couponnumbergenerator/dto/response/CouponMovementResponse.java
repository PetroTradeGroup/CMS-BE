package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.MovementType;
import com.couponnumbergenerator.model.CouponMovement;

import java.time.LocalDateTime;

public record CouponMovementResponse(
        Long id,
        MovementType movementType,
        CouponStatus fromStatus,
        CouponStatus toStatus,
        LocationResponse fromLocation,
        LocationResponse toLocation,
        String performedBy,
        String reason,
        String batchNumber,
        String referenceType,
        Long referenceId,
        LocalDateTime createdAt
) {
    /** batchNumber is the coupon's batch — the same for every movement of that coupon, not the movement's own reference. */
    public static CouponMovementResponse from(CouponMovement movement, String batchNumber) {
        return new CouponMovementResponse(
                movement.getId(),
                movement.getMovementType(),
                movement.getFromStatus(),
                movement.getToStatus(),
                movement.getFromLocation() == null ? null : LocationResponse.from(movement.getFromLocation()),
                movement.getToLocation() == null ? null : LocationResponse.from(movement.getToLocation()),
                movement.getPerformedBy(),
                movement.getReason(),
                batchNumber,
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getCreatedAt()
        );
    }
}