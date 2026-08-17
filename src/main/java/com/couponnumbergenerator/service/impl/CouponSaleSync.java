package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.config.ErpSalesProperties;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.event.SaleAssignedEvent;
import com.couponnumbergenerator.model.CouponSale;
import com.couponnumbergenerator.repository.CouponSaleRepository;
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
 * Drives {@link CouponSalePusher}: an immediate async attempt right after a sale is
 * assigned, plus a periodic sweep of everything still ASSIGNED (an ASSIGNED sale <em>is</em>
 * the retry queue entry — nothing extra is persisted). Mirrors {@link ErpRedemptionSync}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponSaleSync {

    private static final int RETRY_BATCH_SIZE = 50;

    private final ErpSalesProperties properties;
    private final CouponSalePusher pusher;
    private final CouponSaleRepository couponSaleRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSaleAssigned(SaleAssignedEvent event) {
        if (!properties.enabled()) {
            return;
        }
        tryPush(event.saleId());
    }

    @Scheduled(fixedDelayString = "${app.erp.sales.retry-interval-ms:300000}",
            initialDelayString = "${app.erp.sales.retry-interval-ms:300000}")
    public void retryPendingPushes() {
        if (!properties.enabled()) {
            return;
        }
        List<CouponSale> pending = couponSaleRepository
                .findByStatus(SaleStatus.ASSIGNED, PageRequest.of(0, RETRY_BATCH_SIZE, Sort.by("assignedAt")))
                .getContent();
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retrying BC confirmation for {} assigned sale(s)", pending.size());
        pending.forEach(sale -> tryPush(sale.getId()));
    }

    private void tryPush(Long saleId) {
        try {
            pusher.pushToErp(saleId);
        } catch (Exception ex) {
            log.warn("BC confirmation failed for sale {} — left ASSIGNED for retry: {}", saleId, ex.getMessage());
        }
    }
}