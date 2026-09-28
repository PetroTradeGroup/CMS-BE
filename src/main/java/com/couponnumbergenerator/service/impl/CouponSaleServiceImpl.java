package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.ErpSaleRequest;
import com.couponnumbergenerator.dto.response.CouponSaleResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.SaleLineStatus;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.event.SaleAssignedEvent;
import com.couponnumbergenerator.exception.CouponSaleNotFoundException;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponSale;
import com.couponnumbergenerator.model.CouponSaleLine;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.CouponSaleRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.CouponSaleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4 ERP sales integration (Option A — "coupon-blind ERP"): resolves an inbound BC sale
 * event into CMS serials. See {@code docs/erp-sales-integration-design.md} for the full design.
 *
 * <p>One inbound event carries one or more lines (fuel type + denomination + quantity). Each
 * line is assigned or fails on stock independently; the sale's status is the rollup —
 * {@code ASSIGNED} (all lines), {@code PARTIALLY_ASSIGNED} (some), {@code FAILED} (none).
 *
 * <p>A line flagged {@code wholeBooks} is filled with whole intact books (100/100 still
 * IN_STOCK) from the front of the selling queue, skipping any partially-consumed book
 * (§11.4). A loose line draws {@code quantity} coupons in selling order, rolling across book
 * and batch boundaries as needed (§11.2).
 *
 * <p>The assignment holds {@code FOR UPDATE} locks on the drawn coupon rows, so concurrent
 * sales at one site serialise rather than collide. The rare transient failures that remain —
 * a deadlock loser (multi-denomination documents locking pools in opposite orders), or an
 * optimistic-lock clash on a coupon touched by another flow between the lock and the flush —
 * are retried: {@link #receiveSale} re-runs the whole assignment in a fresh transaction up to
 * {@link #MAX_ASSIGNMENT_ATTEMPTS} times before giving up (§11.5).
 */
@Slf4j
@Service
public class CouponSaleServiceImpl implements CouponSaleService {

    /** Recorded as performedBy on coupons allocated via an ERP sale — there's no human actor. */
    private static final String ERP_SALE_ACTOR = "ERP-SALE";

    /** How many times {@link #receiveSale} re-runs the assignment on transient DB contention. */
    static final int MAX_ASSIGNMENT_ATTEMPTS = 3;

    /** Short pause between retries, to let the winning transaction clear rather than re-collide immediately. */
    private static final long RETRY_BACKOFF_MILLIS = 50L;

    private final CouponSaleRepository couponSaleRepository;
    private final CouponRepository couponRepository;
    private final LocationRepository locationRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final CouponLifecycleService couponLifecycleService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionOperations assignmentTransaction;

    public CouponSaleServiceImpl(CouponSaleRepository couponSaleRepository,
                                 CouponRepository couponRepository,
                                 LocationRepository locationRepository,
                                 FuelTypeRepository fuelTypeRepository,
                                 CouponLifecycleService couponLifecycleService,
                                 ApplicationEventPublisher eventPublisher,
                                 @Qualifier("saleAssignmentTransactionTemplate") TransactionOperations assignmentTransaction) {
        this.couponSaleRepository = couponSaleRepository;
        this.couponRepository = couponRepository;
        this.locationRepository = locationRepository;
        this.fuelTypeRepository = fuelTypeRepository;
        this.couponLifecycleService = couponLifecycleService;
        this.eventPublisher = eventPublisher;
        this.assignmentTransaction = assignmentTransaction;
    }

    /**
     * Runs {@link #assignSale} in its own transaction, retrying the whole assignment on
     * transient DB contention (deadlock loser, optimistic-lock failure). Idempotency makes a
     * retry safe: a rolled-back attempt persisted nothing, so the next attempt starts clean.
     */
    @Override
    public CouponSaleResponse receiveSale(ErpSaleRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return assignmentTransaction.execute(status -> assignSale(request));
            } catch (TransientDataAccessException ex) {
                if (attempt >= MAX_ASSIGNMENT_ATTEMPTS) {
                    log.error("Sale {} assignment failed after {} attempt(s) — last error: {}",
                            request.documentNumber(), attempt, ex.toString());
                    throw ex;
                }
                log.warn("Sale {} assignment attempt {}/{} hit {} — retrying",
                        request.documentNumber(), attempt, MAX_ASSIGNMENT_ATTEMPTS,
                        ex.getClass().getSimpleName());
                backoffBeforeRetry();
            }
        }
    }

    private static void backoffBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying sale assignment", ie);
        }
    }

    CouponSaleResponse assignSale(ErpSaleRequest request) {
        CouponSale existing = couponSaleRepository.findByBcDocumentNumber(request.documentNumber()).orElse(null);
        if (existing != null) {
            log.info("Sale event for BC document {} already received (sale #{}) — no-op",
                    request.documentNumber(), existing.getId());
            return CouponSaleResponse.from(existing);
        }

        Location location = resolveLocation(request.locationCode());

        // Persisted up front so each line's allocation can reference the sale id, and so a
        // line's coupons leave the IN_STOCK pool before the next line's (locking) query runs.
        CouponSale sale = couponSaleRepository.save(CouponSale.builder()
                .bcDocumentNumber(request.documentNumber())
                .location(location)
                .status(SaleStatus.RECEIVED)
                .customerReference(request.customerReference())
                .build());

        int assignedLines = 0;
        int lineNumber = 0;
        for (ErpSaleRequest.Line requestLine : request.lines()) {
            lineNumber++;
            requestLine.validateQuantity();
            FuelType fuelType = resolveFuelType(requestLine.fuelTypeId());

            CouponSaleLine line = CouponSaleLine.builder()
                    .lineNumber(lineNumber)
                    .fuelType(fuelType)
                    .denomination(requestLine.denomination())
                    .requestedCount(requestLine.resolvedQuantity())
                    .wholeBooks(requestLine.wholeBooksOrFalse())
                    .build();

            LineSelection selection = selectForLine(location, fuelType, requestLine);
            if (selection.failed()) {
                line.setStatus(SaleLineStatus.FAILED);
                line.setFailureReason(selection.failureReason());
                log.warn("Sale {} line {} FAILED: {}",
                        sale.getBcDocumentNumber(), lineNumber, selection.failureReason());
            } else {
                couponLifecycleService.allocateForSale(selection.coupons(), ERP_SALE_ACTOR, sale.getId());
                line.setStatus(SaleLineStatus.ASSIGNED);
                line.setCouponNumbers(selection.coupons().stream().map(Coupon::getCouponNumber).toList());
                assignedLines++;
            }
            sale.addLine(line);
        }

        SaleStatus rollup = rollUp(assignedLines, sale.getLines().size());
        sale.setStatus(rollup);
        if (assignedLines > 0) {
            sale.setAssignedAt(LocalDateTime.now());
        }
        CouponSale saved = couponSaleRepository.save(sale);

        log.info("Sale {} {} ({} of {} line(s) assigned) at {} (sale #{})",
                saved.getBcDocumentNumber(), rollup, assignedLines, saved.getLines().size(),
                location.getCode(), saved.getId());

        if (assignedLines > 0) {
            eventPublisher.publishEvent(new SaleAssignedEvent(saved.getId()));
        }
        return CouponSaleResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponSaleResponse getByDocumentNumber(String bcDocumentNumber) {
        return couponSaleRepository.findByBcDocumentNumber(bcDocumentNumber)
                .map(CouponSaleResponse::from)
                .orElseThrow(() -> new CouponSaleNotFoundException(bcDocumentNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CouponSaleResponse> getSales(SaleStatus status, Pageable pageable) {
        return PagedResponse.from((status == null
                ? couponSaleRepository.findAll(pageable)
                : couponSaleRepository.findByStatus(status, pageable))
                .map(CouponSaleResponse::from));
    }

    private static SaleStatus rollUp(int assignedLines, int totalLines) {
        if (assignedLines == 0) {
            return SaleStatus.FAILED;
        }
        return assignedLines == totalLines ? SaleStatus.ASSIGNED : SaleStatus.PARTIALLY_ASSIGNED;
    }

    private Location resolveLocation(String locationCode) {
        return locationRepository.findByCode(locationCode)
                .orElseThrow(() -> new LocationNotFoundException(
                        "Unknown location code from BC: '%s' — check Location.code is in sync with Business Central"
                                .formatted(locationCode)));
    }

    private FuelType resolveFuelType(Long fuelTypeId) {
        return fuelTypeRepository.findById(fuelTypeId)
                .orElseThrow(() -> new FuelTypeNotFoundException(fuelTypeId));
    }

    /** Picks the coupons for one line — loose count or whole books — or returns a failure reason. */
    private LineSelection selectForLine(Location location, FuelType fuelType, ErpSaleRequest.Line line) {
        String department = CouponConstants.DEFAULT_DEPARTMENT_CODE;
        if (line.wholeBooksOrFalse()) {
            return selectWholeBooks(location, fuelType, line, department);
        }
        int quantity = line.resolvedQuantity();
        List<Coupon> issuable = couponRepository.findIssuableForSale(
                location.getId(), fuelType.getId(), line.denomination(), department, quantity);
        if (issuable.size() < quantity) {
            return LineSelection.failed("Only %d of %d requested %s coupon(s) available at %s".formatted(
                    issuable.size(), quantity, line.denomination().toPlainString(), location.getCode()));
        }
        return LineSelection.of(issuable);
    }

    /**
     * Fills a whole-book line with the first N intact books (100/100 still IN_STOCK) at the
     * front of the selling queue, skipping any partially-consumed or damaged book (§11.4 /
     * §11.7 Q2). Locks the whole eligible pool and buckets it into books in memory — see
     * {@link CouponRepository#lockIssuablePoolForSale} for why the grouping isn't done in SQL.
     */
    private LineSelection selectWholeBooks(Location location, FuelType fuelType, ErpSaleRequest.Line line,
                                           String department) {
        int quantity = line.resolvedQuantity();
        if (quantity % CouponConstants.BOOK_SIZE != 0) {
            throw new IllegalArgumentException(
                    "Whole-book sale line for %s at %s must request a multiple of %d coupons, got %d".formatted(
                            line.denomination().toPlainString(), location.getCode(),
                            CouponConstants.BOOK_SIZE, quantity));
        }
        int booksWanted = quantity / CouponConstants.BOOK_SIZE;

        List<Coupon> pool = couponRepository.lockIssuablePoolForSale(
                location.getId(), fuelType.getId(), line.denomination(), department);

        // Bucket the locked (all-IN_STOCK) pool into books, preserving selling order. A book
        // whose bucket holds fewer than BOOK_SIZE coupons has some serial already gone
        // (loose sale, damage, cancellation) and is not intact — skip it.
        Map<String, List<Coupon>> books = new LinkedHashMap<>();
        for (Coupon coupon : pool) {
            if (coupon.getBookNumber() == null
                    || coupon.getBatch() == null || coupon.getBatch().getSequenceNumber() == null) {
                continue;
            }
            String bookKey = coupon.getBatch().getSequenceNumber() + "#" + coupon.getBookNumber();
            books.computeIfAbsent(bookKey, k -> new ArrayList<>()).add(coupon);
        }

        List<Coupon> chosen = new ArrayList<>();
        int intactBooks = 0;
        for (List<Coupon> book : books.values()) {
            if (book.size() != CouponConstants.BOOK_SIZE) {
                continue;
            }
            chosen.addAll(book);
            if (++intactBooks == booksWanted) {
                break;
            }
        }

        if (intactBooks < booksWanted) {
            return LineSelection.failed("Only %d of %d requested intact book(s) of %s available at %s".formatted(
                    intactBooks, booksWanted, line.denomination().toPlainString(), location.getCode()));
        }
        return LineSelection.of(chosen);
    }

    /** One line's chosen coupons, or a failure reason when stock fell short. */
    private record LineSelection(List<Coupon> coupons, String failureReason) {
        static LineSelection of(List<Coupon> coupons) {
            return new LineSelection(coupons, null);
        }

        static LineSelection failed(String reason) {
            return new LineSelection(List.of(), reason);
        }

        boolean failed() {
            return failureReason != null;
        }
    }
}
