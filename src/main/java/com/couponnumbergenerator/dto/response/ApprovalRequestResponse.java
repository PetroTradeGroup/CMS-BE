package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.model.CouponApprovalRequest;

import java.time.LocalDateTime;
import java.util.List;

public record ApprovalRequestResponse(
        Long id,
        ApprovalRequestType requestType,
        int count,
        List<DenominationCountResponse> denominations,
        List<Integer> batchSequences,
        List<String> couponNumbers,
        String batchNumber,
        Integer rangeStart,
        Integer rangeEnd,
        CouponStatus targetStatus,
        LocationResponse toLocation,
        DepartmentResponse toDepartment,
        String reason,
        String requestedBy,
        LocalDateTime requestedAt,
        ApprovalStatus status,
        String decidedBy,
        LocalDateTime decidedAt,
        String decisionReason,
        String receivedBy,
        LocalDateTime receivedAt,
        Long requisitionId,
        List<TransferredCouponResponse> transferredCoupons
) {
    public static ApprovalRequestResponse from(CouponApprovalRequest request) {
        return from(request, List.of());
    }

    /**
     * {@code count}, {@code denominations}, and {@code batchSequences} describe how many coupons
     * (in total, broken down by denomination, and by their position in the batch) this request
     * covers — all three fixed at creation and accurate at every stage (PENDING, TRANSFERSHIPMENT,
     * TRANSRECEIPT, ...) — unlike {@code transferredCoupons}, which is populated only whenever this
     * specific call actually moved coupons (approve or confirm-receipt) and is {@code []} otherwise.
     */
    public static ApprovalRequestResponse from(CouponApprovalRequest request, List<TransferredCouponResponse> transferredCoupons) {
        return new ApprovalRequestResponse(
                request.getId(),
                request.getRequestType(),
                request.getCouponCount(),
                request.getDenominationBreakdown().stream().map(DenominationCountResponse::from).toList(),
                request.getBatchSequences(),
                request.getCouponNumbers(),
                request.getBatch() == null ? null : request.getBatch().getBatchNumber(),
                request.getRangeStart(),
                request.getRangeEnd(),
                request.getTargetStatus(),
                request.getToLocation() == null ? null : LocationResponse.from(request.getToLocation()),
                request.getToDepartment() == null ? null : DepartmentResponse.from(request.getToDepartment()),
                request.getReason(),
                request.getRequestedBy(),
                request.getRequestedAt(),
                request.getStatus(),
                request.getDecidedBy(),
                request.getDecidedAt(),
                request.getDecisionReason(),
                request.getReceivedBy(),
                request.getReceivedAt(),
                request.getRequisition() == null ? null : request.getRequisition().getId(),
                transferredCoupons
        );
    }
}
