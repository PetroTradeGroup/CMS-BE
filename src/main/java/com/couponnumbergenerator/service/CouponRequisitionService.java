package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.CreateRequisitionRequest;
import com.couponnumbergenerator.dto.request.FulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.RequisitionDecisionRequest;
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

    /** Declines a pending requisition outright; a reason is required. */
    RequisitionResponse reject(Long requisitionId, RequisitionDecisionRequest decision);

    RequisitionResponse getRequisition(Long requisitionId);

    /** The requisition queue, optionally filtered by status (defaults to all). */
    PagedResponse<RequisitionResponse> getRequisitions(RequisitionStatus status, Pageable pageable);
}