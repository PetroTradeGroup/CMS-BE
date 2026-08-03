package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.RequisitionStatus;
import com.couponnumbergenerator.model.CouponRequisition;

import java.time.LocalDateTime;
import java.util.List;

public record RequisitionResponse(
        Long id,
        DepartmentResponse department,
        LocationResponse location,
        List<RequisitionLineResponse> lines,
        String requestedBy,
        LocalDateTime requestedAt,
        RequisitionStatus status,
        String decidedBy,
        LocalDateTime decidedAt,
        String decisionReason
) {
    public static RequisitionResponse from(CouponRequisition requisition) {
        return new RequisitionResponse(
                requisition.getId(),
                DepartmentResponse.from(requisition.getRequestingDepartment()),
                LocationResponse.from(requisition.getLocation()),
                requisition.getLines().stream().map(RequisitionLineResponse::from).toList(),
                requisition.getRequestedBy(),
                requisition.getRequestedAt(),
                requisition.getStatus(),
                requisition.getDecidedBy(),
                requisition.getDecidedAt(),
                requisition.getDecisionReason()
        );
    }
}