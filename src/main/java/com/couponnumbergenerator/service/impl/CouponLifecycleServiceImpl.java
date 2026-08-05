package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.constants.CouponConstants;
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
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.TransferResultResponse;
import com.couponnumbergenerator.dto.response.TransferredCouponResponse;
import com.couponnumbergenerator.dto.response.TransitionResultResponse;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.MovementType;
import com.couponnumbergenerator.enums.RequisitionStatus;
import com.couponnumbergenerator.event.RedemptionSubmittedEvent;
import com.couponnumbergenerator.exception.ApprovalAlreadyDecidedException;
import com.couponnumbergenerator.exception.ApprovalRequestNotFoundException;
import com.couponnumbergenerator.exception.CouponBatchNotFoundException;
import com.couponnumbergenerator.exception.CouponLocationMismatchException;
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
import com.couponnumbergenerator.service.ActionOutcome;
import com.couponnumbergenerator.service.BulkConfigService;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        return couponApprovalRequestRepository.findById(approvalRequestId)
                .map(ApprovalRequestResponse::from)
                .orElseThrow(() -> new ApprovalRequestNotFoundException(approvalRequestId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ApprovalRequestResponse> getApprovalRequests(ApprovalRequestType requestType, ApprovalStatus status, Pageable pageable) {
        Page<CouponApprovalRequest> page;
        if (requestType == null && status == null) {
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
        Set<String> couponNumbers = new LinkedHashSet<>();
        if (request.scannedPayloads() != null) {
            request.scannedPayloads().forEach(payload -> couponNumbers.add(qrCodeService.decodeAndVerify(payload)));
        }
        if (request.couponNumbers() != null) {
            couponNumbers.addAll(request.couponNumbers());
        }
        if (couponNumbers.isEmpty()) {
            throw new IllegalArgumentException("At least one of scannedPayloads or couponNumbers is required");
        }
        validateBatchSize(couponNumbers.size());
        List<Coupon> coupons = loadCoupons(couponNumbers);

        Location location = resolveLocation(request.locationId());
        for (Coupon coupon : coupons) {
            if (!coupon.getCurrentLocation().getId().equals(location.getId())) {
                throw new CouponLocationMismatchException(coupon.getCouponNumber(),
                        coupon.getCurrentLocation().getCode(), location.getCode());
            }
            if (!CouponStateMachine.canTransition(coupon.getStatus(), CouponStatus.REDEEMED)) {
                throw new InvalidStatusTransitionException(coupon.getCouponNumber(), coupon.getStatus(), CouponStatus.REDEEMED);
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
                .reason(request.reason())
                .requestedBy(request.performedBy())
                .build());
        log.info("Submitted {} coupon(s) for redemption at {} by {} (request #{})",
                coupons.size(), location.getCode(), request.performedBy(), approval.getId());
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
        applyTransition(coupons, CouponStatus.REDEEMED, null, null, movementReason, request.performedBy(),
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
            List<Coupon> eligible = couponRepository
                    .findByBatchIdAndDenominationOrderByBatchSequenceAsc(request.batchId(), line.denomination())
                    .stream()
                    .filter(coupon -> isSelectableForDenomination(coupon, request.targetStatus(), departmentHandoff))
                    .limit(line.count())
                    .toList();
            if (eligible.size() < line.count() && !allowPartial) {
                throw new IllegalArgumentException(
                        "Batch %d has only %d eligible coupon(s) of denomination %s, but %d were requested"
                                .formatted(request.batchId(), eligible.size(),
                                        line.denomination().toPlainString(), line.count()));
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
        List<String> numbers = List.copyOf(couponNumbers);
        List<Coupon> coupons = new ArrayList<>(numbers.size());
        for (int i = 0; i < numbers.size(); i += LOAD_CHUNK_SIZE) {
            coupons.addAll(couponRepository.findByCouponNumberIn(
                    numbers.subList(i, Math.min(i + LOAD_CHUNK_SIZE, numbers.size()))));
        }
        if (coupons.size() < couponNumbers.size()) {
            throw new CouponNotFoundException(describeMissing(couponNumbers, coupons));
        }
        return coupons;
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
