package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.ErpRedemptionRequest;
import com.couponnumbergenerator.dto.request.RedemptionPostRequest;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import com.couponnumbergenerator.repository.CouponApprovalRequestRepository;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.ErpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Posts one pending redemption to the ERP and, with the document number the ERP hands back,
 * completes the local post (coupons flip to REDEEMED, request to POSTED) — the automated
 * version of a clerk calling {@code POST /redemptions/{id}/post}. If the ERP call fails the
 * transaction ends with nothing changed and the request stays PENDING, so the manual endpoint
 * and the {@link ErpRedemptionSync} retry sweep both remain able to finish the job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErpRedemptionPoster {

    /** Recorded as performedBy/decidedBy on auto-posted redemptions. */
    static final String ERP_ACTOR = "ERP-AUTO";

    private final CouponApprovalRequestRepository couponApprovalRequestRepository;
    private final ErpClient erpClient;
    private final CouponLifecycleService couponLifecycleService;

    @Transactional
    public void postToErp(Long approvalRequestId) {
        CouponApprovalRequest approval = couponApprovalRequestRepository.findById(approvalRequestId).orElse(null);
        if (approval == null
                || approval.getRequestType() != ApprovalRequestType.REDEMPTION
                || approval.getStatus() != ApprovalStatus.PENDING) {
            return;
        }
        String documentNumber = erpClient.postRedemption(toPayload(approval));
        couponLifecycleService.postRedemption(approvalRequestId,
                new RedemptionPostRequest(documentNumber, null, ERP_ACTOR));
    }

    private ErpRedemptionRequest toPayload(CouponApprovalRequest approval) {
        return new ErpRedemptionRequest(
                approval.getId(),
                approval.getToLocation().getCode(),
                approval.getToLocation().getName(),
                approval.getCouponCount(),
                approval.getDenominationBreakdown().stream()
                        .map(line -> line.getDenomination().multiply(BigDecimal.valueOf(line.getCount())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                approval.getCouponNumbers(),
                approval.getDenominationBreakdown().stream()
                        .map(line -> new ErpRedemptionRequest.Line(line.getDenomination(), line.getCount(),
                                line.getDenomination().multiply(BigDecimal.valueOf(line.getCount()))))
                        .toList(),
                approval.getRequestedBy(),
                approval.getRequestedAt());
    }
}