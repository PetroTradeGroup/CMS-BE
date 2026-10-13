package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.AutoFulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.CreateRequisitionRequest;
import com.couponnumbergenerator.dto.request.FulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.RequisitionDecisionRequest;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.AutoFulfillResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.RequisitionResponse;
import com.couponnumbergenerator.enums.RequisitionStatus;
import org.springframework.data.domain.Pageable;

/**
 * A department's request to Stock for coupons, expressed in litres per denomination — the
 * digital Internal Purchase Requisition. Fulfilled over one or more transfers (see
 * {@link CouponLifecycleService#transferByBatch}), since Stock may only be able to deliver
 * part of what was requested at a time.
 */
public interface CouponRequisitionService {

    /**
     * @param callerDepartmentCode the raiser's {@code department} token claim — when present it decides the
     *                             requesting department and {@code request.departmentId()} is ignored; when
     *                             null (e.g. an Admin with no department) departmentId is required.
     */
    RequisitionResponse create(CreateRequisitionRequest request, String callerDepartmentCode);

    /** Commercial (Manager or Rep) signs off a PENDING requisition → COMMERCIAL_APPROVED. */
    RequisitionResponse approveCommercial(Long requisitionId, String approvedBy);

    /** The Stocks Controller signs off a COMMERCIAL_APPROVED requisition → APPROVED, ready to issue. */
    RequisitionResponse approveStocks(Long requisitionId, String approvedBy);

    /**
     * A Stocks Clerk issues against an APPROVED (or PARTIALLY_FULFILLED) requisition — the GIV: builds
     * the underlying transfer for the litres issued right now (which may be less than what's
     * outstanding — a partial delivery) and, since the Stocks Controller already approved the
     * requisition, issues it straight to IN_TRANSIT. The transfer is linked back to this requisition
     * so its lines' fulfilled-litres update once the receiving department confirms receipt (the GRV).
     */
    ApprovalRequestResponse fulfill(Long requisitionId, FulfillRequisitionRequest request);

    /**
     * Fulfils as much of the requisition's outstanding lines as current stock allows, in whole
     * books, without naming a batch: for each line the batches of its fuel type are walked oldest
     * first (FIFO) and whole books of issuable stock are drawn from each until the line is covered
     * or stock runs out — one deferred transfer per batch drawn from. Litres that don't amount to
     * a whole book stay outstanding.
     */
    AutoFulfillResponse autoFulfill(Long requisitionId, AutoFulfillRequisitionRequest request);

    /** Declines a requisition at any non-terminal stage (closing out the rest of a partial one); a reason is required. */
    RequisitionResponse reject(Long requisitionId, RequisitionDecisionRequest decision);

    RequisitionResponse getRequisition(Long requisitionId);

    /** The requisition queue, optionally filtered by status and/or who raised it (null = no filter). */
    PagedResponse<RequisitionResponse> getRequisitions(RequisitionStatus status, String requestedBy, Pageable pageable);
}