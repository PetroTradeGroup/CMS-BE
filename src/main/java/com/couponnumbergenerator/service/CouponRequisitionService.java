package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.AutoFulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.CreateRequisitionRequest;
import com.couponnumbergenerator.dto.request.FulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.RequisitionDecisionRequest;
import com.couponnumbergenerator.dto.response.AutoFulfillResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.RequisitionResponse;
import com.couponnumbergenerator.dto.response.TransferResultResponse;
import com.couponnumbergenerator.enums.RequisitionStatus;
import org.springframework.data.domain.Pageable;

/**
 * A department's request to Stock for coupons, expressed in litres per denomination — the
 * digital Internal Purchase Requisition. Fulfilled over one or more transfers (see
 * {@link CouponLifecycleService#transferByBatch}), since Stock may only be able to deliver
 * part of what was requested at a time.
 */
public interface CouponRequisitionService {

    RequisitionResponse create(CreateRequisitionRequest request);

    /**
     * Stock's response to a requisition: builds and submits the underlying transfer for the
     * litres being issued right now (which may be less than what's outstanding — a partial
     * delivery). The resulting approval request is linked back to this requisition so its
     * lines' fulfilled-litres update once the receiving department confirms receipt.
     */
    ActionOutcome<TransferResultResponse> fulfill(Long requisitionId, FulfillRequisitionRequest request);

    /**
     * Fulfils as much of the requisition's outstanding lines as current stock allows, in whole
     * books, without naming a batch: for each line the batches of its fuel type are walked oldest
     * first (FIFO) and whole books of issuable stock are drawn from each until the line is covered
     * or stock runs out — one deferred transfer per batch drawn from. Litres that don't amount to
     * a whole book stay outstanding.
     */
    AutoFulfillResponse autoFulfill(Long requisitionId, AutoFulfillRequisitionRequest request);

    /** Declines a pending requisition outright; a reason is required. */
    RequisitionResponse reject(Long requisitionId, RequisitionDecisionRequest decision);

    RequisitionResponse getRequisition(Long requisitionId);

    /** The requisition queue, optionally filtered by status (defaults to all). */
    PagedResponse<RequisitionResponse> getRequisitions(RequisitionStatus status, Pageable pageable);
}