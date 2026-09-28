package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.constants.RedemptionCodes;
import com.couponnumbergenerator.dto.request.ApprovalDecisionRequest;
import com.couponnumbergenerator.dto.request.DenominationLine;
import com.couponnumbergenerator.dto.request.ReceiptConfirmationRequest;
import com.couponnumbergenerator.dto.request.ReceiveBatchRequest;
import com.couponnumbergenerator.dto.request.RedemptionPostRequest;
import com.couponnumbergenerator.dto.request.RedemptionSubmitRequest;
import com.couponnumbergenerator.dto.request.TransferRequest;
import com.couponnumbergenerator.dto.request.TransitionRequest;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.CouponMovementResponse;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.ScanResponse;
import com.couponnumbergenerator.dto.response.TransferResultResponse;
import com.couponnumbergenerator.dto.response.TransferredCouponResponse;
import com.couponnumbergenerator.dto.response.TransitionResultResponse;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.MovementType;
import com.couponnumbergenerator.enums.RequisitionStatus;
import com.couponnumbergenerator.event.RedemptionSubmittedEvent;
import com.couponnumbergenerator.exception.ApprovalAlreadyDecidedException;
import com.couponnumbergenerator.exception.ApprovalRequestNotFoundException;
import com.couponnumbergenerator.exception.CouponAlreadyPendingRedemptionException;
import com.couponnumbergenerator.exception.CouponBatchNotFoundException;
import com.couponnumbergenerator.exception.CouponNotFoundException;
import com.couponnumbergenerator.exception.DepartmentNotFoundException;
import com.couponnumbergenerator.exception.InvalidStatusTransitionException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.exception.ReceiptNotAwaitedException;
import com.couponnumbergenerator.lifecycle.CouponStateMachine;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.model.CouponMovement;
import com.couponnumbergenerator.model.CouponRequisition;
import com.couponnumbergenerator.model.Department;
import com.couponnumbergenerator.model.DenominationCount;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.model.RequisitionLine;
import com.couponnumbergenerator.repository.CouponApprovalRequestRepository;
import com.couponnumbergenerator.repository.CouponBatchRepository;
import com.couponnumbergenerator.repository.CouponMovementRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.DepartmentRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.security.DepartmentAccessGuard;
import com.couponnumbergenerator.security.LocationAccessGuard;
import com.couponnumbergenerator.specification.ApprovalRequestSpecification;
import com.couponnumbergenerator.service.ActionOutcome;
import com.couponnumbergenerator.service.BulkConfigService;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.couponnumbergenerator.enums.CouponStatus.IN_STOCK;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponLifecycleServiceImpl implements CouponLifecycleService {

    private static final int LOAD_CHUNK_SIZE = 1000;
    private static final int MAX_MISSING_REPORTED = 20;
    private static final Set<CouponStatus> REASON_REQUIRED = Set.of(CouponStatus.CANCELLED, CouponStatus.FLAGGED);

    private final CouponRepository couponRepository;
    private final CouponMovementRepository couponMovementRepository;
    private final CouponBatchRepository couponBatchRepository;
    private final LocationRepository locationRepository;
    private final DepartmentRepository departmentRepository;
    private final CouponApprovalRequestRepository couponApprovalRequestRepository;
    private final DepartmentAccessGuard departmentAccessGuard;
    private final LocationAccessGuard locationAccessGuard;
    private final BulkConfigService bulkConfigService;
    private final QrCodeService qrCodeService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ActionOutcome<TransitionResultResponse> transition(TransitionRequest request) {
        Set<String> couponNumbers = new LinkedHashSet<>(request.couponNumbers());
        validateBatchSize(couponNumbers.size());
        rejectInternallyManagedTargetStatus(request.targetStatus());
        requireReasonIfNeeded(request.targetStatus(), request.reason());
        List<Coupon> coupons = loadCoupons(couponNumbers);

        if (request.toLocationId() != null) {
            assertAllCanTransition(coupons, request.targetStatus(), false);
            Location toLocation = resolveLocation(request.toLocationId());
            CouponApprovalRequest approval = persistApprovalRequest(ApprovalRequestType.TRANSITION,
                    List.copyOf(couponNumbers), coupons, null, null, null, request.targetStatus(), toLocation, null,
                    request.reason(), request.performedBy());
            log.info("Deferred transition of {} coupon(s) to {} for supervisor approval (request #{})",
                    coupons.size(), request.targetStatus(), approval.getId());
            return new ActionOutcome.Pending<>(ApprovalRequestResponse.from(approval));
        }

        applyTransition(coupons, request.targetStatus(), null, null, request.reason(), request.performedBy());
        log.info("Transitioned {} coupon(s) to {}", coupons.size(), request.targetStatus());
        return new ActionOutcome.Applied<>(new TransitionResultResponse(coupons.size(), request.targetStatus()));
    }

    @Override
    @Transactional
    public TransitionResultResponse receiveBatch(Long batchId, ReceiveBatchRequest request) {
        CouponBatch batch = couponBatchRepository.findById(batchId)
                .orElseThrow(() -> new CouponBatchNotFoundException(batchId));

        Location location = request.locationId() == null
                ? batch.getOriginLocation()
                : resolveLocation(request.locationId());

        List<Coupon> coupons = couponRepository.findByBatchIdAndStatus(batchId, CouponStatus.GENERATED);
        if (coupons.isEmpty()) {
            throw new IllegalArgumentException(
                    "Batch %s has no coupons awaiting receipt".formatted(batch.getBatchNumber()));
        }

        applyTransition(coupons, IN_STOCK, location, null, null, request.performedBy());
        log.info("Received {} coupon(s) of batch {} into stock at {}",
                coupons.size(), batch.getBatchNumber(), location.getCode());
        return new TransitionResultResponse(coupons.size(), IN_STOCK);
    }

    @Override
    @Transactional
    public void recordGeneration(List<Coupon> coupons, String performedBy) {
        List<CouponMovement> movements = new ArrayList<>(coupons.size());
        for (Coupon coupon : coupons) {
            movements.add(CouponMovement.builder()
                    .coupon(coupon)
                    .movementType(CouponStateMachine.movementFor(null, coupon.getStatus()))
                    .toStatus(coupon.getStatus())
                    .toLocation(coupon.getCurrentLocation())
                    .performedBy(performedBy)
                    .referenceType(coupon.getBatch() == null ? null : "BATCH")
                    .referenceId(coupon.getBatch() == null ? null : coupon.getBatch().getId())
                    .build());
        }
        couponMovementRepository.saveAll(movements);
    }

    @Override
    @Transactional
    public void allocateForSale(List<Coupon> coupons, String performedBy, Long saleId) {
        applyTransition(coupons, CouponStatus.ALLOCATED, null, null,
                "ERP sale", performedBy, "COUPON_SALE", saleId);
    }

    @Override
    @Transactional
    public void transitionForBankPurchase(List<Coupon> coupons, CouponStatus target, String reason,
                                          String performedBy, Long purchaseId) {
        applyTransition(coupons, target, null, null, reason, performedBy, "BANK_PURCHASE", purchaseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponMovementResponse> getHistory(String couponNumber) {
        Coupon coupon = couponRepository.findByCouponNumber(couponNumber)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + couponNumber));
        String batchNumber = coupon.getBatch() == null ? null : coupon.getBatch().getBatchNumber();
        return couponMovementRepository.findByCouponIdOrderByCreatedAtAsc(coupon.getId()).stream()
                .map(movement -> CouponMovementResponse.from(movement, batchNumber))
                .toList();
    }

    @Override
    @Transactional
    public ActionOutcome<TransferResultResponse> transferByBatch(TransferRequest request, boolean allowPartialDenominationFulfillment) {
        CouponBatch batch = couponBatchRepository.findById(request.batchId())
                .orElseThrow(() -> new CouponBatchNotFoundException(request.batchId()));

        validateTransferRequest(request, batch);
        if (request.targetStatus() != null) {
            requireReasonIfNeeded(request.targetStatus(), request.reason());
        }

        List<Coupon> coupons;
        boolean byDenomination = request.denominationLines() != null && !request.denominationLines().isEmpty();
        if (byDenomination) {
            coupons = selectByDenomination(request, allowPartialDenominationFulfillment);
            if (coupons.isEmpty()) {
                throw new IllegalArgumentException(
                        "Batch %d has no eligible coupons for any requested denomination"
                                .formatted(request.batchId()));
            }
        } else {
            coupons = selectBatchCoupons(request.batchId(), request.rangeStart(), request.rangeEnd());
            if (coupons.isEmpty()) {
                throw new IllegalArgumentException(
                        "No coupons found for batch %d in the given range (legacy batches generated before batch position tracking cannot be range-selected)"
                                .formatted(request.batchId()));
            }
        }
        validateBatchSize(coupons.size());

        if (request.toLocationId() != null || request.toDepartmentId() != null) {
            assertAllCanTransition(coupons, request.targetStatus(), request.toDepartmentId() != null);
            Location toLocation = request.toLocationId() == null ? null : resolveLocation(request.toLocationId());
            Department toDepartment = request.toDepartmentId() == null ? null : resolveDepartment(request.toDepartmentId());
            // A denomination pick is "first N eligible right now" — pin the exact coupons so the
            // approved move applies to what the requester saw, not to a re-run of the selection.
            List<String> pinnedNumbers = byDenomination
                    ? coupons.stream().map(Coupon::getCouponNumber).toList()
                    : List.of();
            CouponApprovalRequest approval = persistApprovalRequest(ApprovalRequestType.TRANSFER,
                    pinnedNumbers, coupons, batch, request.rangeStart(), request.rangeEnd(), request.targetStatus(),
                    toLocation, toDepartment, request.reason(), request.performedBy());
            log.info("Deferred transfer of {} coupon(s) of batch {} for supervisor approval (request #{})",
                    coupons.size(), batch.getBatchNumber(), approval.getId());
            return new ActionOutcome.Pending<>(ApprovalRequestResponse.from(approval));
        }

        applyTransition(coupons, request.targetStatus(), null, null, request.reason(), request.performedBy());
        log.info("Transferred {} coupon(s) of batch {}", coupons.size(), batch.getBatchNumber());
        return new ActionOutcome.Applied<>(new TransferResultResponse(coupons.size(), batch.getId(), batch.getBatchNumber(),
                request.rangeStart(), request.rangeEnd(), request.denominationLines(),
                coupons.stream().map(TransferredCouponResponse::from).toList(), request.targetStatus()));
    }

    @Override
    @Transactional
    public ApprovalRequestResponse approve(Long approvalRequestId, ApprovalDecisionRequest decision) {
        CouponApprovalRequest approval = resolvePendingApproval(approvalRequestId);

        // Coupon numbers are set for TRANSITION requests and for denomination-picked transfers
        // (whose exact coupons were pinned at request time); range/whole-batch transfers re-select.
        List<Coupon> coupons = !approval.getCouponNumbers().isEmpty()
                ? loadCoupons(new LinkedHashSet<>(approval.getCouponNumbers()))
                : selectBatchCoupons(approval.getBatch().getId(), approval.getRangeStart(), approval.getRangeEnd());
        if (coupons.isEmpty()) {
            throw new IllegalArgumentException(
                    "No coupons found for approval request %d — the underlying batch range is now empty"
                            .formatted(approvalRequestId));
        }

        boolean departmentHandoff = approval.getRequestType() == ApprovalRequestType.TRANSFER
                && approval.getToDepartment() != null;

        if (departmentHandoff) {
            departmentAccessGuard.assertDepartment(approval.getFromDepartment().getCode(),
                    SecurityContextHolder.getContext().getAuthentication());
            // Re-validate eligibility explicitly (status AND department) rather than relying solely
            // on applyTransition's canTransition(from, IN_TRANSIT) check below — that only catches a
            // status drift (e.g. ALLOCATED elsewhere since the request), not a coupon that stayed
            // IN_STOCK but drifted out of the STOCKS department in the meantime.
            coupons.forEach(this::assertEligibleForReassignment);
            // Stock has authorized the issue, but the coupons aren't in the receiving department's
            // custody yet — they move to IN_TRANSIT only. The real target status (if any) and the
            // toLocation/toDepartment are applied once the receiving department confirms receipt.
            applyTransition(coupons, CouponStatus.IN_TRANSIT, null, null, approval.getReason(), approval.getRequestedBy());
            approval.setCouponNumbers(coupons.stream().map(Coupon::getCouponNumber).toList());
            approval.setStatus(ApprovalStatus.TRANSFERSHIPMENT);
        } else {
            applyTransition(coupons, approval.getTargetStatus(), approval.getToLocation(), approval.getToDepartment(),
                    approval.getReason(), approval.getRequestedBy());
            approval.setStatus(ApprovalStatus.APPROVED);
        }

        approval.setDecidedBy(decision.approvedBy());
        approval.setDecidedAt(LocalDateTime.now());
        approval.setDecisionReason(decision.reason());
        log.info("Approval request {} {} by {} — {} coupon(s) {}", approvalRequestId, approval.getStatus(),
                decision.approvedBy(), coupons.size(), departmentHandoff ? "issued, awaiting receipt" : "moved");

        List<TransferredCouponResponse> movedCoupons = approval.getRequestType() == ApprovalRequestType.TRANSFER
                ? coupons.stream().map(TransferredCouponResponse::from).toList()
                : List.of();
        return ApprovalRequestResponse.from(approval, movedCoupons);
    }

    @Override
    @Transactional
    public ApprovalRequestResponse confirmReceipt(Long approvalRequestId, ReceiptConfirmationRequest request) {
        CouponApprovalRequest approval = couponApprovalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new ApprovalRequestNotFoundException(approvalRequestId));
        if (approval.getStatus() != ApprovalStatus.TRANSFERSHIPMENT) {
            throw new ReceiptNotAwaitedException(approvalRequestId, approval.getStatus());
        }
        departmentAccessGuard.assertDepartment(approval.getToDepartment().getCode(),
                SecurityContextHolder.getContext().getAuthentication());

        List<Coupon> coupons = loadCoupons(new LinkedHashSet<>(approval.getCouponNumbers()));
        CouponStatus finalStatus = approval.getTargetStatus() == null ? IN_STOCK : approval.getTargetStatus();
        applyTransition(coupons, finalStatus, approval.getToLocation(), approval.getToDepartment(),
                approval.getReason(), approval.getRequestedBy());

        approval.setStatus(ApprovalStatus.TRANSRECEIPT);
        approval.setReceivedBy(request.receivedBy());
        approval.setReceivedAt(LocalDateTime.now());
        if (request.reason() != null && !request.reason().isBlank()) {
            approval.setDecisionReason(approval.getDecisionReason() == null
                    ? request.reason()
                    : approval.getDecisionReason() + " | Receipt: " + request.reason());
        }
        if (approval.getRequisition() != null) {
            recordRequisitionFulfillment(approval.getRequisition(), coupons);
        }

        log.info("Approval request {} received by {} — {} coupon(s) moved into {}",
                approvalRequestId, request.receivedBy(), coupons.size(), finalStatus);
        return ApprovalRequestResponse.from(approval, coupons.stream().map(TransferredCouponResponse::from).toList());
    }

    /** Credits each matching requisition line (by fuel type + denomination) with the litres just delivered; flips the requisition to FULFILLED once every line is satisfied. */
    private void recordRequisitionFulfillment(CouponRequisition requisition, List<Coupon> coupons) {
        for (RequisitionLine line : requisition.getLines()) {
            BigDecimal delivered = coupons.stream()
                    .filter(coupon -> coupon.getDenomination().compareTo(line.getDenomination()) == 0
                            && coupon.getFuelType().getId().equals(line.getFuelType().getId()))
                    .map(Coupon::getDenomination)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (delivered.compareTo(BigDecimal.ZERO) > 0) {
                line.setFulfilledLitres(line.getFulfilledLitres().add(delivered));
            }
        }
        requisition.setStatus(requisition.getLines().stream().allMatch(RequisitionLine::isFullyFulfilled)
                ? RequisitionStatus.FULFILLED
                : RequisitionStatus.PARTIALLY_FULFILLED);
    }

    @Override
    @Transactional
    public ApprovalRequestResponse reject(Long approvalRequestId, ApprovalDecisionRequest decision) {
        if (decision.reason() == null || decision.reason().isBlank()) {
            throw new IllegalArgumentException("A reason is required when rejecting an approval request");
        }
        CouponApprovalRequest approval = resolvePendingApproval(approvalRequestId);

        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setDecidedBy(decision.approvedBy());
        approval.setDecidedAt(LocalDateTime.now());
        approval.setDecisionReason(decision.reason());
        log.info("Approval request {} rejected by {}", approvalRequestId, decision.approvedBy());
        return ApprovalRequestResponse.from(approval);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalRequestResponse getApprovalRequest(Long approvalRequestId) {
        CouponApprovalRequest approval = couponApprovalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new ApprovalRequestNotFoundException(approvalRequestId));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String callerLocationCode = locationAccessGuard.callerLocationCode(authentication);
        if (callerLocationCode != null) {
            boolean stationMismatch = approval.getToLocation() == null
                    || !callerLocationCode.equals(approval.getToLocation().getCode());
            boolean notMine = !locationAccessGuard.callerIsTeamLeader(authentication)
                    && !locationAccessGuard.callerUsername(authentication).equals(approval.getRequestedBy());
            if (stationMismatch || notMine) {
                // Station-scoped caller requesting someone else's approval — 404, not 403, so a
                // guessed ID doesn't confirm it exists.
                throw new ApprovalRequestNotFoundException(approvalRequestId);
            }
        }
        return ApprovalRequestResponse.from(approval);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ApprovalRequestResponse> getApprovalRequests(ApprovalRequestType requestType, ApprovalStatus status, Pageable pageable) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String callerLocationCode = locationAccessGuard.callerLocationCode(authentication);

        Page<CouponApprovalRequest> page;
        if (callerLocationCode != null) {
            // Station-scoped caller: Team Leader sees every request at their site; a plain
            // Attendant is narrowed further to just their own.
            String callerUsername = locationAccessGuard.callerIsTeamLeader(authentication)
                    ? null
                    : locationAccessGuard.callerUsername(authentication);
            page = couponApprovalRequestRepository.findAll(
                    ApprovalRequestSpecification.withFilters(requestType, status, callerLocationCode, callerUsername),
                    pageable);
        } else if (requestType == null && status == null) {
            page = couponApprovalRequestRepository.findAll(pageable);
        } else if (requestType == null) {
            page = couponApprovalRequestRepository.findByStatus(status, pageable);
        } else if (status == null) {
            page = couponApprovalRequestRepository.findByRequestType(requestType, pageable);
        } else {
            page = couponApprovalRequestRepository.findByRequestTypeAndStatus(requestType, status, pageable);
        }
        return PagedResponse.from(page.map(ApprovalRequestResponse::from));
    }

    @Override
    @Transactional
    public ApprovalRequestResponse submitRedemption(RedemptionSubmitRequest request) {
        // Only station staff (Attendant/Team Leader) redeem: the signed token decides where and who,
        // never the request body — it can't be spoofed to claim a different station.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String callerLocationCode = locationAccessGuard.callerLocationCode(authentication);
        if (callerLocationCode == null) {
            throw new AccessDeniedException("Your account isn't assigned to a station — ask an admin to set its location");
        }
        Location location = locationRepository.findByCode(callerLocationCode)
                .orElseThrow(() -> new LocationNotFoundException(callerLocationCode));
        String performedBy = locationAccessGuard.callerUsername(authentication);

        Set<String> couponNumbers = new LinkedHashSet<>();
        if (request.scannedPayloads() != null) {
            request.scannedPayloads().forEach(payload -> couponNumbers.add(qrCodeService.decodeAndVerify(payload)));
        }
        Set<String> typedNumbers = request.couponNumbers() == null ? Set.of() : Set.copyOf(request.couponNumbers());
        couponNumbers.addAll(typedNumbers);
        if (request.redemptionCodes() != null) {
            couponNumbers.addAll(resolveRedemptionCodes(request.redemptionCodes(), performedBy));
        }
        if (couponNumbers.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one of scannedPayloads, couponNumbers or redemptionCodes is required");
        }
        validateBatchSize(couponNumbers.size());
        // Locked, so a concurrent submission of the same coupon waits here and then sees ours as
        // pending. Sorted so chunks take their locks in one global order (no deadlocks).
        List<Coupon> coupons = loadCoupons(new TreeSet<>(couponNumbers), couponRepository::lockByCouponNumberIn);

        // A virtual coupon's number isn't secret (it shows in lists and exports) — only the QR or
        // the customer's redemption code proves the customer is holding it.
        for (Coupon coupon : coupons) {
            if (coupon.getCouponType() == CouponType.DIGITAL && typedNumbers.contains(coupon.getCouponNumber())) {
                throw new IllegalArgumentException(
                        "Virtual coupon %s must be redeemed by QR scan or redemption code, not its coupon number"
                                .formatted(coupon.getCouponNumber()));
            }
        }

        // Single use from the moment of submission: fuel is dispensed on submit, long before posting.
        List<String> alreadyPending = couponApprovalRequestRepository.findCouponNumbersInRequests(
                ApprovalRequestType.REDEMPTION, ApprovalStatus.PENDING, couponNumbers);
        if (!alreadyPending.isEmpty()) {
            throw new CouponAlreadyPendingRedemptionException(alreadyPending);
        }

        // No currentLocation check here: REDEEMED is only reachable from ALLOCATED
        // (CouponStateMachine), meaning every coupon redeemable here has already been sold to a
        // customer — they can redeem it at any site, not just wherever it happened to be stocked
        // before the sale.
        LocalDate today = LocalDate.now();
        for (Coupon coupon : coupons) {
            if (!CouponStateMachine.canTransition(coupon.getStatus(), CouponStatus.REDEEMED)) {
                throw new InvalidStatusTransitionException(coupon.getCouponNumber(), coupon.getStatus(), CouponStatus.REDEEMED);
            }
            // ponytail: checked at the pump, not swept — nothing moves coupons to EXPIRED yet; add a
            // nightly job if reports need expired stock counted by status.
            if (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(today)) {
                throw new IllegalArgumentException("Coupon %s expired on %s".formatted(
                        coupon.getCouponNumber(), coupon.getExpiryDate()));
            }
        }

        CouponApprovalRequest approval = couponApprovalRequestRepository.save(CouponApprovalRequest.builder()
                .requestType(ApprovalRequestType.REDEMPTION)
                .couponNumbers(coupons.stream().map(Coupon::getCouponNumber).toList())
                .couponCount(coupons.size())
                .denominationBreakdown(denominationBreakdown(coupons))
                .batchSequences(batchSequences(coupons))
                .targetStatus(CouponStatus.REDEEMED)
                .toLocation(location)
                .carRegistrationNumber(request.carRegistrationNumber().trim().toUpperCase())
                .reason(request.reason())
                .requestedBy(performedBy)
                .build());
        log.info("Submitted {} coupon(s) for redemption at {} by {} (request #{})",
                coupons.size(), location.getCode(), performedBy, approval.getId());
        eventPublisher.publishEvent(new RedemptionSubmittedEvent(approval.getId()));
        return ApprovalRequestResponse.from(approval);
    }

    @Override
    @Transactional
    public ApprovalRequestResponse postRedemption(Long approvalRequestId, RedemptionPostRequest request) {

        CouponApprovalRequest approval = resolvePendingApproval(approvalRequestId);
        if (approval.getRequestType() != ApprovalRequestType.REDEMPTION) {
            throw new IllegalArgumentException(
                    "Approval request %d is not a redemption request".formatted(approvalRequestId));
        }

        List<Coupon> coupons = loadCoupons(new LinkedHashSet<>(approval.getCouponNumbers()));
        String movementReason = request.reason() != null ? request.reason() : approval.getReason();
        // toLocation moves the coupon's currentLocation to the redeeming site — it may differ from
        // wherever the coupon was stocked/sold, since an ALLOCATED coupon is redeemable anywhere.
        applyTransition(coupons, CouponStatus.REDEEMED, approval.getToLocation(), null, movementReason, request.performedBy(),
                "REDEMPTION_REQUEST", approval.getId());

        approval.setDocumentNumber(request.documentNumber());
        approval.setStatus(ApprovalStatus.POSTED);
        approval.setDecidedBy(request.performedBy());
        approval.setDecidedAt(LocalDateTime.now());
        if (request.reason() != null && !request.reason().isBlank()) {
            approval.setDecisionReason(request.reason());
        }
        log.info("Redemption request {} posted by {} with document number {} — {} coupon(s) redeemed",
                approvalRequestId, request.performedBy(), request.documentNumber(), coupons.size());
        return ApprovalRequestResponse.from(approval);
    }

    private CouponApprovalRequest resolvePendingApproval(Long approvalRequestId) {
        CouponApprovalRequest approval = couponApprovalRequestRepository.findById(approvalRequestId)
                .orElseThrow(() -> new ApprovalRequestNotFoundException(approvalRequestId));
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new ApprovalAlreadyDecidedException(approvalRequestId, approval.getStatus());
        }
        return approval;
    }

    private CouponApprovalRequest persistApprovalRequest(ApprovalRequestType type, List<String> couponNumbers,
                                                          List<Coupon> coupons, CouponBatch batch, Integer rangeStart, Integer rangeEnd,
                                                          CouponStatus targetStatus, Location toLocation, Department toDepartment,
                                                          String reason, String requestedBy) {
        // A department handoff is only ever eligible from coupons currently IN_STOCK and in the
        // STOCKS department (assertAllCanTransition/isEligibleForReassignment) — so every coupon in
        // this batch shares the same origin department, safe to snapshot from the first one (AD-3).
        Department fromDepartment = toDepartment == null ? null : coupons.get(0).getCurrentDepartment();
        return couponApprovalRequestRepository.save(CouponApprovalRequest.builder()
                .requestType(type)
                .couponNumbers(couponNumbers)
                .couponCount(coupons.size())
                .denominationBreakdown(denominationBreakdown(coupons))
                .batchSequences(batchSequences(coupons))
                .batch(batch)
                .rangeStart(rangeStart)
                .rangeEnd(rangeEnd)
                .targetStatus(targetStatus)
                .toLocation(toLocation)
                .toDepartment(toDepartment)
                .fromDepartment(fromDepartment)
                .reason(reason)
                .requestedBy(requestedBy)
                .build());
    }

    /** Groups the resolved coupons by denomination, in ascending order, for the approval request's fixed-at-creation breakdown. */
    private List<DenominationCount> denominationBreakdown(List<Coupon> coupons) {
        Map<BigDecimal, Long> grouped = coupons.stream()
                .collect(Collectors.groupingBy(Coupon::getDenomination, TreeMap::new, Collectors.counting()));
        return grouped.entrySet().stream()
                .map(entry -> new DenominationCount(entry.getKey(), entry.getValue().intValue()))
                .toList();
    }

    /**
     * Batch positions of the resolved coupons, ascending — for matching against a physical coupon
     * book during a count. Coupons generated before batch position tracking existed have no
     * sequence and are left out rather than polluting the list with nulls.
     */
    private List<Integer> batchSequences(List<Coupon> coupons) {
        return coupons.stream()
                .map(Coupon::getBatchSequence)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    /**
     * Dry-run legality check so an obviously-doomed request is rejected before it ever reaches the
     * approval queue. A null {@code target}, or a department handoff (Stock issuing to another
     * department, whatever the target status), is only eligible from coupons currently IN_STOCK and
     * still sitting in the STOCKS department — a coupon already allocated, redeemed, flagged,
     * mid-transfer (IN_TRANSIT), or moved out of STOCKS can't be issued out again. A department
     * handoff routes through IN_TRANSIT at approval time (see {@link #approve}), which requires the
     * coupon to genuinely be in Stock's own custody beforehand.
     */
    private void assertAllCanTransition(List<Coupon> coupons, CouponStatus target, boolean departmentHandoff) {
        for (Coupon coupon : coupons) {
            if (departmentHandoff || target == null) {
                assertEligibleForReassignment(coupon);
            }
            if (target != null && !CouponStateMachine.canTransition(coupon.getStatus(), target)) {
                throw new InvalidStatusTransitionException(coupon.getCouponNumber(), coupon.getStatus(), target);
            }
        }
    }

    /** Pure reassignment eligibility: the coupon must be IN_STOCK and currently in the STOCKS department. */
    private void assertEligibleForReassignment(Coupon coupon) {
        if (!isEligibleForReassignment(coupon)) {
            if (!CouponStateMachine.isInStock(coupon.getStatus())) {
                throw new InvalidStatusTransitionException(coupon.getCouponNumber(), coupon.getStatus());
            }
            String departmentCode = coupon.getCurrentDepartment() == null ? null : coupon.getCurrentDepartment().getCode();
            throw new InvalidStatusTransitionException(
                    coupon.getCouponNumber(), departmentCode, CouponConstants.DEFAULT_DEPARTMENT_CODE);
        }
    }

    private boolean isEligibleForReassignment(Coupon coupon) {
        return CouponStateMachine.isInStock(coupon.getStatus())
                && coupon.getCurrentDepartment() != null
                && CouponConstants.DEFAULT_DEPARTMENT_CODE.equals(coupon.getCurrentDepartment().getCode());
    }

    /**
     * For each {@link DenominationLine}, selects the first {@code count} coupons of that denomination,
     * in batch order, that are currently eligible for the requested move: for a pure reassignment or
     * a department handoff (Stock issuing to another department), the coupon must be IN_STOCK in the
     * STOCKS department; otherwise, able to make the {@code targetStatus} change per the state
     * machine. A coupon that isn't eligible (already allocated, redeemed, mid-transfer, etc.) is
     * skipped in favor of the next eligible one at that denomination — so a repeat "give out N more"
     * call keeps finding fresh stock instead of re-colliding with coupons an earlier call already
     * moved. Fails with {@code 400} up front, before the request ever reaches the approval queue, if
     * a line's denomination doesn't have {@code count} *eligible* coupons in the batch — unless
     * {@code allowPartial} is set, in which case a short line contributes whatever eligible coupons
     * it found (down to zero) instead of failing the whole request. A coupon has a single fixed
     * denomination, so lines for different denominations never compete for the same coupon.
     */
    private List<Coupon> selectByDenomination(TransferRequest request, boolean allowPartial) {
        boolean departmentHandoff = request.toDepartmentId() != null;
        List<Coupon> selected = new ArrayList<>();
        for (DenominationLine line : request.denominationLines()) {
            line.validateQuantity();
            int requested = line.resolvedCount();
            List<Coupon> eligible = couponRepository
                    .findByBatchIdAndDenominationOrderByBatchSequenceAsc(request.batchId(), line.denomination())
                    .stream()
                    .filter(coupon -> isSelectableForDenomination(coupon, request.targetStatus(), departmentHandoff))
                    .limit(requested)
                    .toList();
            if (eligible.size() < requested && !allowPartial) {
                throw new IllegalArgumentException(
                        "Batch %d has only %d eligible coupon(s) of denomination %s, but %d were requested"
                                .formatted(request.batchId(), eligible.size(),
                                        line.denomination().toPlainString(), requested));
            }
            selected.addAll(eligible);
        }
        return selected;
    }

    private boolean isSelectableForDenomination(Coupon coupon, CouponStatus target, boolean departmentHandoff) {
        if ((departmentHandoff || target == null) && !isEligibleForReassignment(coupon)) {
            return false;
        }
        return target == null || CouponStateMachine.canTransition(coupon.getStatus(), target);
    }

    private List<Coupon> selectBatchCoupons(Long batchId, Integer rangeStart, Integer rangeEnd) {
        return rangeStart == null
                ? couponRepository.findByBatchIdOrderByBatchSequenceAsc(batchId)
                : couponRepository.findByBatchIdAndBatchSequenceBetweenOrderByBatchSequenceAsc(batchId, rangeStart, rangeEnd);
    }

    private void validateTransferRequest(TransferRequest request, CouponBatch batch) {
        boolean rangeStartGiven = request.rangeStart() != null;
        boolean rangeEndGiven = request.rangeEnd() != null;
        if (rangeStartGiven != rangeEndGiven) {
            throw new IllegalArgumentException("rangeStart and rangeEnd must both be provided, or both omitted");
        }
        if (rangeStartGiven && (request.rangeStart() < 1 || request.rangeEnd() < request.rangeStart())) {
            throw new IllegalArgumentException("Invalid range: rangeStart must be >= 1 and <= rangeEnd");
        }
        if (rangeEndGiven && request.rangeEnd() > batch.getQuantity()) {
            throw new IllegalArgumentException(
                    "rangeEnd %d exceeds batch quantity %d".formatted(request.rangeEnd(), batch.getQuantity()));
        }
        List<DenominationLine> lines = request.denominationLines();
        boolean denominationLinesGiven = lines != null && !lines.isEmpty();
        if (denominationLinesGiven && rangeStartGiven) {
            throw new IllegalArgumentException(
                    "Select by position range (rangeStart/rangeEnd) or by denominationLines, not both");
        }
        if (denominationLinesGiven) {
            // BigDecimal.equals() is scale-sensitive (20 != 20.00) — compare by numeric value instead.
            Set<BigDecimal> distinctDenominations = new TreeSet<>(Comparator.naturalOrder());
            lines.forEach(line -> distinctDenominations.add(line.denomination()));
            if (distinctDenominations.size() < lines.size()) {
                throw new IllegalArgumentException("denominationLines must not repeat the same denomination");
            }
        }
        if (request.targetStatus() == null && request.toLocationId() == null && request.toDepartmentId() == null) {
            throw new IllegalArgumentException(
                    "At least one of targetStatus, toLocationId, or toDepartmentId must be provided");
        }
        rejectInternallyManagedTargetStatus(request.targetStatus());
    }

    /**
     * IN_TRANSIT is a transitional status the system sets and clears on its own — {@link #approve}
     * moves a department-handoff's coupons into it, {@link #confirmReceipt} moves them back out —
     * a caller must never request it directly. Letting it through used to pass validation cleanly
     * (IN_STOCK -> IN_TRANSIT is a legal transition) and only blow up later at confirmReceipt time,
     * when the coupon is already IN_TRANSIT and the same requested target is applied again
     * (IN_TRANSIT -> IN_TRANSIT isn't a legal self-transition) — rejecting it up front at request
     * time surfaces the mistake immediately instead of two steps downstream.
     */
    private void rejectInternallyManagedTargetStatus(CouponStatus target) {
        if (target == CouponStatus.IN_TRANSIT) {
            throw new IllegalArgumentException(
                    "targetStatus IN_TRANSIT cannot be requested directly — it's set automatically when a "
                            + "department-handoff transfer is approved, and cleared automatically when the "
                            + "receiving department confirms receipt");
        }
    }

    private void applyTransition(List<Coupon> coupons, CouponStatus target, Location toLocation, Department toDepartment,
                                 String reason, String performedBy) {
        applyTransition(coupons, target, toLocation, toDepartment, reason, performedBy, null, null);
    }

    /**
     * Applies a status/location/department change to every coupon, all-or-nothing.
     * {@code target == null} means "keep the current status" — a pure location/department
     * reassignment, recorded as {@link MovementType#REASSIGNMENT} instead of a derived
     * lifecycle movement. {@code referenceType}/{@code referenceId} are stamped onto each
     * {@link CouponMovement} when the caller has an originating record to link back to (e.g. a
     * redemption request) — null for a plain transition/transfer.
     */
    private void applyTransition(List<Coupon> coupons, CouponStatus target, Location toLocation, Department toDepartment,
                                 String reason, String performedBy, String referenceType, Long referenceId) {
        List<CouponMovement> movements = new ArrayList<>(coupons.size());
        for (Coupon coupon : coupons) {
            CouponStatus from = coupon.getStatus();
            if (target != null) {
                if (!CouponStateMachine.canTransition(from, target)) {
                    throw new InvalidStatusTransitionException(coupon.getCouponNumber(), from, target);
                }
            } else {
                assertEligibleForReassignment(coupon);
            }
            Location fromLocation = coupon.getCurrentLocation();
            Department fromDepartment = coupon.getCurrentDepartment();
            if (target != null) {
                coupon.setStatus(target);
            }
            if (toLocation != null) {
                coupon.setCurrentLocation(toLocation);
            }
            if (toDepartment != null) {
                coupon.setCurrentDepartment(toDepartment);
            }
            MovementType movementType = target == null
                    ? MovementType.REASSIGNMENT
                    : CouponStateMachine.movementFor(from, target);
            movements.add(CouponMovement.builder()
                    .coupon(coupon)
                    .movementType(movementType)
                    .fromStatus(from)
                    .toStatus(target == null ? from : target)
                    .fromLocation(fromLocation)
                    .toLocation(toLocation != null ? toLocation : fromLocation)
                    .fromDepartment(fromDepartment)
                    .toDepartment(toDepartment != null ? toDepartment : fromDepartment)
                    .performedBy(performedBy)
                    .reason(reason)
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .build());
        }
        couponRepository.saveAll(coupons);
        couponMovementRepository.saveAll(movements);
    }

    private List<Coupon> loadCoupons(Set<String> couponNumbers) {
        return loadCoupons(couponNumbers, couponRepository::findByCouponNumberIn);
    }

    private List<Coupon> loadCoupons(Set<String> couponNumbers, Function<List<String>, List<Coupon>> finder) {
        List<String> numbers = List.copyOf(couponNumbers);
        List<Coupon> coupons = new ArrayList<>(numbers.size());
        for (int i = 0; i < numbers.size(); i += LOAD_CHUNK_SIZE) {
            coupons.addAll(finder.apply(numbers.subList(i, Math.min(i + LOAD_CHUNK_SIZE, numbers.size()))));
        }
        if (coupons.size() < couponNumbers.size()) {
            throw new CouponNotFoundException(describeMissing(couponNumbers, coupons));
        }
        return coupons;
    }

    @Override
    @Transactional(readOnly = true)
    public ScanResponse previewRedemption(String couponNumber) {
        return preview(couponRepository.findByCouponNumber(couponNumber)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + couponNumber)));
    }

    @Override
    @Transactional(readOnly = true)
    public ScanResponse previewRedemptionByCode(String redemptionCode) {
        return preview(couponRepository.findByRedemptionCode(RedemptionCodes.normalize(redemptionCode))
                .orElseThrow(() -> {
                    // Repeated misses from one user may be someone guessing codes — worth watching.
                    log.warn("Redemption code lookup by {} found nothing", locationAccessGuard.callerUsername(
                            SecurityContextHolder.getContext().getAuthentication()));
                    return new CouponNotFoundException("No coupon for that redemption code");
                }));
    }

    private ScanResponse preview(Coupon coupon) {
        boolean pending = !couponApprovalRequestRepository.findCouponNumbersInRequests(
                ApprovalRequestType.REDEMPTION, ApprovalStatus.PENDING, List.of(coupon.getCouponNumber())).isEmpty();
        return ScanResponse.of(CouponResponse.from(coupon), pending, LocalDate.now());
    }

    /** Maps typed redemption codes to coupon numbers; any unknown code fails the whole submission. */
    private Set<String> resolveRedemptionCodes(List<String> rawCodes, String performedBy) {
        Set<String> codes = new LinkedHashSet<>();
        rawCodes.forEach(code -> codes.add(RedemptionCodes.normalize(code)));
        List<Coupon> found = couponRepository.findByRedemptionCodeIn(codes);
        if (found.size() < codes.size()) {
            Set<String> unknown = new LinkedHashSet<>(codes);
            found.forEach(coupon -> unknown.remove(coupon.getRedemptionCode()));
            // Repeated unknown codes from one user may be someone guessing — worth watching in the logs.
            log.warn("Redemption by {} rejected: {} unknown redemption code(s)", performedBy, unknown.size());
            throw new CouponNotFoundException("Unknown redemption code(s): " + String.join(", ", unknown));
        }
        Set<String> numbers = new LinkedHashSet<>();
        found.forEach(coupon -> numbers.add(coupon.getCouponNumber()));
        return numbers;
    }

    private String describeMissing(Set<String> requested, List<Coupon> found) {
        Set<String> missing = new LinkedHashSet<>(requested);
        found.forEach(coupon -> missing.remove(coupon.getCouponNumber()));
        List<String> sample = missing.stream().limit(MAX_MISSING_REPORTED).toList();
        String suffix = missing.size() > MAX_MISSING_REPORTED
                ? " and %d more".formatted(missing.size() - MAX_MISSING_REPORTED)
                : "";
        return "Coupons not found: " + String.join(", ", sample) + suffix;
    }

    private void validateBatchSize(int size) {
        int maxCount = bulkConfigService.getMaxCount();
        if (size > maxCount) {
            throw new IllegalArgumentException(
                    "Selected %d coupon(s) exceeds the configured maximum of %d".formatted(size, maxCount));
        }
    }

    private void requireReasonIfNeeded(CouponStatus target, String reason) {
        if (REASON_REQUIRED.contains(target) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException(
                    "A reason is required when transitioning coupons to " + target);
        }
    }

    private Location resolveLocation(Long locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new LocationNotFoundException(locationId));
    }

    private Department resolveDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
    }
}
