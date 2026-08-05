package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.config.ErpProperties;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.event.RedemptionSubmittedEvent;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import com.couponnumbergenerator.repository.CouponApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Drives {@link ErpRedemptionPoster}: an immediate async attempt right after a redemption is
 * submitted, plus a periodic sweep of everything still PENDING (the "retry in background"
 * queue — a PENDING redemption <em>is</em> the queue entry, so nothing extra is persisted).
 * A failed attempt only logs; the manual post endpoint stays available throughout.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErpRedemptionSync {

    private static final int RETRY_BATCH_SIZE = 50;

    private final ErpProperties properties;
    private final ErpRedemptionPoster poster;
    private final CouponApprovalRequestRepository couponApprovalRequestRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRedemptionSubmitted(RedemptionSubmittedEvent event) {
        if (!properties.enabled()) {
            return;
        }
        tryPost(event.approvalRequestId());
    }

    @Scheduled(fixedDelayString = "${app.erp.retry-interval-ms:300000}",
            initialDelayString = "${app.erp.retry-interval-ms:300000}")
    public void retryPendingRedemptions() {
        if (!properties.enabled()) {
            return;
        }
        List<CouponApprovalRequest> pending = couponApprovalRequestRepository
                .findByRequestTypeAndStatus(ApprovalRequestType.REDEMPTION, ApprovalStatus.PENDING,
                        PageRequest.of(0, RETRY_BATCH_SIZE, Sort.by("requestedAt")))
                .getContent();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retrying ERP posting for {} pending redemption(s)", pending.size());
        pending.forEach(approval -> tryPost(approval.getId()));
    }

    private void tryPost(Long approvalRequestId) {
        try {
            poster.postToErp(approvalRequestId);
        } catch (Exception ex) {
            log.warn("ERP posting failed for redemption request {} — left PENDING for retry: {}",
                    approvalRequestId, ex.getMessage());
        }
    }
}