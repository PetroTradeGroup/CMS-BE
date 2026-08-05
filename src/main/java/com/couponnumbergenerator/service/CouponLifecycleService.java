package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.ApprovalDecisionRequest;
import com.couponnumbergenerator.dto.request.ReceiptConfirmationRequest;
import com.couponnumbergenerator.dto.request.ReceiveBatchRequest;
import com.couponnumbergenerator.dto.request.RedemptionPostRequest;
import com.couponnumbergenerator.dto.request.RedemptionSubmitRequest;
import com.couponnumbergenerator.dto.request.TransferRequest;
import com.couponnumbergenerator.dto.request.TransitionRequest;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.CouponMovementResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.TransferResultResponse;
import com.couponnumbergenerator.dto.response.TransitionResultResponse;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.model.Coupon;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Single choke point for all coupon status changes. Every transition is validated against
 * the {@link com.couponnumbergenerator.lifecycle.CouponStateMachine} and recorded as an
 * immutable {@link com.couponnumbergenerator.model.CouponMovement}.
 */
public interface CouponLifecycleService {

    /**
     * Transitions a set of coupons to a target status, all-or-nothing: if any coupon is
     * missing or its transition is illegal, nothing is changed. If {@code toLocationId} is
     * set, the move is deferred for supervisor approval instead of applying immediately.
     */
    ActionOutcome<TransitionResultResponse> transition(TransitionRequest request);

    /**
     * Receives a batch into stock: all GENERATED coupons of the batch move to IN_STOCK
     * at the given location (defaults to the batch's origin location). Not gated by
     * approval — this is the batch arriving at its first location, not an inter-location move.
     */
    TransitionResultResponse receiveBatch(Long batchId, ReceiveBatchRequest request);

    /**
     * Records the GENERATION movement for freshly persisted coupons.
     * Must be called within the same transaction that created them.
     */
    void recordGeneration(List<Coupon> coupons, String performedBy);

    /** Full movement history of a coupon, oldest first. */
    List<CouponMovementResponse> getHistory(String couponNumber);

    /**
     * Moves a whole batch, or a 1-indexed inclusive position range within it, to a new
     * location and/or department, optionally with a status change. If {@code targetStatus}
     * is omitted the coupons keep their current status — a pure reassignment. All-or-nothing,
     * same as {@link #transition}. If {@code toLocationId} and/or {@code toDepartmentId} is
     * set, the move is deferred for supervisor approval instead of applying immediately.
     */
    default ActionOutcome<TransferResultResponse> transferByBatch(TransferRequest request) {
        return transferByBatch(request, false);
    }

    /**
     * Same as {@link #transferByBatch(TransferRequest)}, but for a denomination-line selection,
     * {@code allowPartialDenominationFulfillment} lets a line take however many eligible coupons
     * the batch actually has (down to zero) instead of rejecting the whole request when it falls
     * short of the requested count — used by {@code CouponRequisitionService#fulfill} so Stock can
     * draw down whatever a batch has and leave the rest outstanding, rather than requisition
     * fulfillment being all-or-nothing per batch. Still fails if not a single eligible coupon is
     * found for any line. Position range and whole-batch selections are unaffected by this flag —
     * they're always all-or-nothing, since one ineligible coupon in a pinned range/batch selection
     * means the caller's assumption about what they were selecting no longer holds.
     */
    ActionOutcome<TransferResultResponse> transferByBatch(TransferRequest request, boolean allowPartialDenominationFulfillment);

    /**
     * Approves a pending request and executes it, re-validating against each coupon's
     * current state (self-healing if something changed since the request was made). For a
     * TRANSFER that changes department, this only moves the coupons to IN_TRANSIT — the real
     * target status and toLocation/toDepartment aren't applied until {@link #confirmReceipt}.
     */
    ApprovalRequestResponse approve(Long approvalRequestId, ApprovalDecisionRequest decision);

    /** Rejects a pending request; no coupons are touched. A reason is required. */
    ApprovalRequestResponse reject(Long approvalRequestId, ApprovalDecisionRequest decision);

    /**
     * The receiving department's confirmation that a TRANSFERSHIPMENT department-handoff transfer
     * actually arrived — the digital GRV/GIV "Goods Received By" signature. Applies the transfer's
     * real target status (or IN_STOCK if none) and toLocation/toDepartment, re-validating against
     * each coupon's current state. Only valid while the approval request is TRANSFERSHIPMENT.
     */
    ApprovalRequestResponse confirmReceipt(Long approvalRequestId, ReceiptConfirmationRequest request);

    ApprovalRequestResponse getApprovalRequest(Long approvalRequestId);

    /** The supervisor's queue, optionally filtered by status (defaults to all). */
    default PagedResponse<ApprovalRequestResponse> getApprovalRequests(ApprovalStatus status, Pageable pageable) {
        return getApprovalRequests(null, status, pageable);
    }

    /** Same as {@link #getApprovalRequests(ApprovalStatus, Pageable)}, additionally filterable by request type. */
    PagedResponse<ApprovalRequestResponse> getApprovalRequests(ApprovalRequestType requestType, ApprovalStatus status, Pageable pageable);

    /**
     * Submits a batch of coupons for redemption (Duties 4-5: count/sign the redemption form,
     * scan-verify against it) as a PENDING {@code REDEMPTION} request — no coupon status
     * changes yet. Coupons are resolved either from scanned, HMAC-signed QR payloads (verified
     * via {@link com.couponnumbergenerator.service.QrCodeService#decodeAndVerify}) or from
     * manually-entered coupon numbers (fallback for a damaged QR). Every resolved coupon must
     * currently sit at {@code locationId} — the site asserting the redemption — since there's
     * no authenticated session yet to derive that from; a mismatch fails the whole batch.
     */
    ApprovalRequestResponse submitRedemption(RedemptionSubmitRequest request);

    /**
     * Posts a pending redemption request (Duty 6: record in Navision with a document number),
     * re-validating each coupon is still ALLOCATED (all-or-nothing) before flipping it to
     * REDEEMED. Only valid while the request is PENDING and of type REDEMPTION.
     */
    ApprovalRequestResponse postRedemption(Long approvalRequestId, RedemptionPostRequest request);
}
