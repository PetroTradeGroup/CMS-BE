package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.AutoFulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.CreateRequisitionRequest;
import com.couponnumbergenerator.dto.request.DenominationLine;
import com.couponnumbergenerator.dto.request.FulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.RequisitionDecisionRequest;
import com.couponnumbergenerator.dto.request.RequisitionLineRequest;
import com.couponnumbergenerator.dto.request.TransferRequest;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.AutoFulfillResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.RequisitionResponse;
import com.couponnumbergenerator.dto.response.TransferResultResponse;
import com.couponnumbergenerator.enums.RequisitionStatus;
import com.couponnumbergenerator.exception.ApprovalRequestNotFoundException;
import com.couponnumbergenerator.exception.CouponBatchNotFoundException;
import com.couponnumbergenerator.exception.DepartmentNotFoundException;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.exception.RequisitionAlreadyDecidedException;
import com.couponnumbergenerator.exception.RequisitionNotFoundException;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.model.CouponRequisition;
import com.couponnumbergenerator.model.Department;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.model.RequisitionLine;
import com.couponnumbergenerator.repository.CouponApprovalRequestRepository;
import com.couponnumbergenerator.repository.CouponBatchRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.CouponRequisitionRepository;
import com.couponnumbergenerator.repository.DepartmentRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.ActionOutcome;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.CouponRequisitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRequisitionServiceImpl implements CouponRequisitionService {

    private final CouponRequisitionRepository couponRequisitionRepository;
    private final CouponApprovalRequestRepository couponApprovalRequestRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final CouponBatchRepository couponBatchRepository;
    private final CouponRepository couponRepository;
    private final CouponLifecycleService couponLifecycleService;

    @Override
    @Transactional
    public RequisitionResponse create(CreateRequisitionRequest request) {
        validateNoDuplicateLines(request.lines());
        Department department = resolveDepartment(request.departmentId());
        Location location = resolveLocation(request.locationId());

        CouponRequisition requisition = CouponRequisition.builder()
                .requestingDepartment(department)
                .location(location)
                .requestedBy(request.requestedBy())
                .build();
        for (RequisitionLineRequest line : request.lines()) {
            line.validateQuantity();
            BigDecimal litres = line.resolvedLitres();
            // Stocks issue physical coupons by the book, so every line must resolve to whole
            // books — request `books` directly, or litres that are an exact book multiple.
            if (litres.remainder(line.bookLitres()).compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException(
                        "Line %s L: %s litres is not a whole number of books — one book is %d × %s L = %s L, so request in books"
                                .formatted(line.denomination().toPlainString(), litres.toPlainString(),
                                        CouponConstants.BOOK_SIZE, line.denomination().toPlainString(),
                                        line.bookLitres().toPlainString()));
            }
            requisition.addLine(RequisitionLine.builder()
                    .fuelType(resolveFuelType(line.fuelTypeId()))
                    .denomination(line.denomination())
                    .requestedLitres(litres)
                    .build());
        }
        CouponRequisition saved = couponRequisitionRepository.save(requisition);
        log.info("Requisition {} raised by {} for department {} ({} line(s))",
                saved.getId(), request.requestedBy(), department.getCode(), request.lines().size());
        return RequisitionResponse.from(saved);
    }

    @Override
    @Transactional
    public ActionOutcome<TransferResultResponse> fulfill(Long requisitionId, FulfillRequisitionRequest request) {
        validateNoDuplicateLines(request.lines());
        CouponRequisition requisition = resolveOpenRequisition(requisitionId);
        CouponBatch batch = couponBatchRepository.findById(request.batchId())
                .orElseThrow(() -> new CouponBatchNotFoundException(request.batchId()));

        List<DenominationLine> denominationLines = new ArrayList<>();
        for (RequisitionLineRequest line : request.lines()) {
            line.validateQuantity();
            BigDecimal litres = line.resolvedLitres();
            RequisitionLine matched = requisition.getLines().stream()
                    .filter(candidate -> candidate.getDenomination().compareTo(line.denomination()) == 0
                            && candidate.getFuelType().getId().equals(line.fuelTypeId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Requisition %d has no line for fuel type %d at denomination %s"
                                    .formatted(requisitionId, line.fuelTypeId(), line.denomination().toPlainString())));
            if (!batch.getFuelType().getId().equals(matched.getFuelType().getId())) {
                throw new IllegalArgumentException(
                        "Batch %d is %s, but the %s L line on requisition %d requests %s"
                                .formatted(request.batchId(), batch.getFuelType().getName(),
                                        line.denomination().toPlainString(), requisitionId,
                                        matched.getFuelType().getName()));
            }
            BigDecimal outstanding = matched.outstandingLitres();
            if (litres.compareTo(outstanding) > 0) {
                throw new IllegalArgumentException(
                        "Requisition %d line %s: %s litres requested but only %s outstanding"
                                .formatted(requisitionId, line.denomination().toPlainString(),
                                        litres.toPlainString(), outstanding.toPlainString()));
            }
            BigDecimal[] countAndRemainder = litres.divideAndRemainder(line.denomination());
            if (countAndRemainder[1].compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException(
                        "%s litres does not divide evenly into %s L coupons"
                                .formatted(litres.toPlainString(), line.denomination().toPlainString()));
            }
            denominationLines.add(new DenominationLine(line.denomination(), countAndRemainder[0].intValueExact()));
        }

        TransferRequest transferRequest = new TransferRequest(
                request.batchId(), null, null, denominationLines,
                requisition.getLocation().getId(), requisition.getRequestingDepartment().getId(),
                request.targetStatus(), request.reason(), request.performedBy());

        // Partial fulfillment is allowed here: a batch may not have enough eligible coupons of a
        // requested denomination on hand, and that's fine — Stock issues what the batch actually
        // has, the shortfall stays outstanding on the requisition line, and another batch can be
        // drawn against later via a fresh fulfill() call.
        ActionOutcome<TransferResultResponse> outcome = couponLifecycleService.transferByBatch(transferRequest, true);
        switch (outcome) {
            case ActionOutcome.Pending<TransferResultResponse> pending -> {
                CouponApprovalRequest approval = couponApprovalRequestRepository.findById(pending.request().id())
                        .orElseThrow(() -> new ApprovalRequestNotFoundException(pending.request().id()));
                approval.setRequisition(requisition);
                log.info("Requisition {} fulfilled via approval request {} ({} line(s))",
                        requisitionId, approval.getId(), denominationLines.size());
            }
            case ActionOutcome.Applied<TransferResultResponse> ignored -> {
                // toDepartmentId is always set above, so transferByBatch always defers — this
                // branch only exists for switch exhaustiveness over the sealed ActionOutcome.
            }
        }
        return outcome;
    }

    @Override
    @Transactional
    public AutoFulfillResponse autoFulfill(Long requisitionId, AutoFulfillRequisitionRequest request) {
        CouponRequisition requisition = resolveOpenRequisition(requisitionId);

        // Plan whole books per batch: for each outstanding line, walk that fuel type's batches
        // oldest first and take as many whole books of issuable stock as each batch can give.
        Map<Long, List<RequisitionLineRequest>> planByBatch = new LinkedHashMap<>();
        Map<Long, List<CouponBatch>> batchesByFuelType = new HashMap<>();
        for (RequisitionLine line : requisition.getLines()) {
            BigDecimal bookLitres = line.getDenomination().multiply(BigDecimal.valueOf(CouponConstants.BOOK_SIZE));
            int booksNeeded = line.outstandingLitres().divideToIntegralValue(bookLitres).intValueExact();
            if (booksNeeded <= 0) {
                continue;
            }
            List<CouponBatch> batches = batchesByFuelType.computeIfAbsent(line.getFuelType().getId(),
                    couponBatchRepository::findByFuelTypeIdOrderByCreatedAtAsc);
            for (CouponBatch batch : batches) {
                if (booksNeeded == 0) {
                    break;
                }
                long issuable = couponRepository.countIssuable(batch.getId(), line.getDenomination(),
                        CouponConstants.DEFAULT_DEPARTMENT_CODE);
                int booksAvailable = (int) (issuable / CouponConstants.BOOK_SIZE);
                if (booksAvailable == 0) {
                    continue;
                }
                int take = Math.min(booksNeeded, booksAvailable);
                planByBatch.computeIfAbsent(batch.getId(), id -> new ArrayList<>())
                        .add(new RequisitionLineRequest(line.getFuelType().getId(), line.getDenomination(),
                                null, take));
                booksNeeded -= take;
            }
        }
        if (planByBatch.isEmpty()) {
            throw new IllegalArgumentException(
                    "No whole books of issuable stock found for the outstanding lines of requisition %d"
                            .formatted(requisitionId));
        }

        List<ApprovalRequestResponse> transfers = new ArrayList<>();
        for (Map.Entry<Long, List<RequisitionLineRequest>> entry : planByBatch.entrySet()) {
            ActionOutcome<TransferResultResponse> outcome = fulfill(requisitionId, new FulfillRequisitionRequest(
                    entry.getKey(), entry.getValue(), request.targetStatus(), request.reason(), request.performedBy()));
            if (outcome instanceof ActionOutcome.Pending<TransferResultResponse> pending) {
                transfers.add(pending.request());
            }
        }
        log.info("Requisition {} auto-fulfilled from {} batch(es) by {} — {} transfer request(s) raised",
                requisitionId, planByBatch.size(), request.performedBy(), transfers.size());
        return new AutoFulfillResponse(getRequisition(requisitionId), transfers);
    }

    @Override
    @Transactional
    public RequisitionResponse reject(Long requisitionId, RequisitionDecisionRequest decision) {
        if (decision.reason() == null || decision.reason().isBlank()) {
            throw new IllegalArgumentException("A reason is required when rejecting a requisition");
        }
        CouponRequisition requisition = resolveOpenRequisition(requisitionId);

        requisition.setStatus(RequisitionStatus.REJECTED);
        requisition.setDecidedBy(decision.decidedBy());
        requisition.setDecidedAt(LocalDateTime.now());
        requisition.setDecisionReason(decision.reason());
        log.info("Requisition {} rejected by {}", requisitionId, decision.decidedBy());
        return RequisitionResponse.from(requisition);
    }

    @Override
    @Transactional(readOnly = true)
    public RequisitionResponse getRequisition(Long requisitionId) {
        return couponRequisitionRepository.findById(requisitionId)
                .map(RequisitionResponse::from)
                .orElseThrow(() -> new RequisitionNotFoundException(requisitionId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<RequisitionResponse> getRequisitions(RequisitionStatus status, Pageable pageable) {
        Page<CouponRequisition> page = status == null
                ? couponRequisitionRepository.findAll(pageable)
                : couponRequisitionRepository.findByStatus(status, pageable);
        return PagedResponse.from(page.map(RequisitionResponse::from));
    }

    /**
     * A requisition can still be acted on (fulfilled further, or rejected to close out what's left
     * outstanding) while PENDING (nothing delivered yet) or PARTIALLY_FULFILLED (some lines short of
     * their requested litres) — only FULFILLED and REJECTED are terminal.
     */
    private CouponRequisition resolveOpenRequisition(Long requisitionId) {
        CouponRequisition requisition = couponRequisitionRepository.findById(requisitionId)
                .orElseThrow(() -> new RequisitionNotFoundException(requisitionId));
        RequisitionStatus status = requisition.getStatus();
        if (status != RequisitionStatus.PENDING && status != RequisitionStatus.PARTIALLY_FULFILLED) {
            throw new RequisitionAlreadyDecidedException(requisitionId, status);
        }
        return requisition;
    }

    private void validateNoDuplicateLines(List<RequisitionLineRequest> lines) {
        // BigDecimal.equals() is scale-sensitive (20 != 20.00) — compare by numeric value instead.
        // A fuel type + denomination pair identifies a line, e.g. 20L petrol and 20L diesel can
        // coexist as separate lines on the same requisition.
        Set<RequisitionLineRequest> distinctLines = new TreeSet<>(
                Comparator.comparing(RequisitionLineRequest::fuelTypeId, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(RequisitionLineRequest::denomination));
        distinctLines.addAll(lines);
        if (distinctLines.size() < lines.size()) {
            throw new IllegalArgumentException("lines must not repeat the same fuel type and denomination");
        }
    }

    private Department resolveDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
    }

    private Location resolveLocation(Long locationId) {
        return locationRepository.findById(locationId)
                .orElseThrow(() -> new LocationNotFoundException(locationId));
    }

    private FuelType resolveFuelType(Long fuelTypeId) {
        return fuelTypeRepository.findById(fuelTypeId)
                .orElseThrow(() -> new FuelTypeNotFoundException(fuelTypeId));
    }
}