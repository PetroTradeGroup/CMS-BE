package com.couponnumbergenerator.integration;

import com.couponnumbergenerator.dto.request.ApprovalDecisionRequest;
import com.couponnumbergenerator.dto.request.CouponFilterRequest;
import com.couponnumbergenerator.dto.request.CreateDepartmentRequest;
import com.couponnumbergenerator.dto.request.CreateLocationRequest;
import com.couponnumbergenerator.dto.request.DenominationLine;
import com.couponnumbergenerator.dto.request.GenerateBulkCouponRequest;
import com.couponnumbergenerator.dto.request.ReceiveBatchRequest;
import com.couponnumbergenerator.dto.request.TransferRequest;
import com.couponnumbergenerator.dto.request.TransitionRequest;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.CouponMovementResponse;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.dto.response.InventorySummaryResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.enums.LocationType;
import com.couponnumbergenerator.enums.MovementType;
import com.couponnumbergenerator.exception.InvalidStatusTransitionException;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.repository.CouponBatchRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.service.ActionOutcome;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.CouponService;
import com.couponnumbergenerator.service.DepartmentService;
import com.couponnumbergenerator.service.InventoryService;
import com.couponnumbergenerator.service.LocationService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end lifecycle flow against a real Postgres (Flyway V1+V2 run on the fresh container):
 * generate physical batch -> receive into stock -> cancel one -> verify history, inventory and 409 rule.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LifecycleFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private CouponService couponService;
    @Autowired private CouponLifecycleService lifecycleService;
    @Autowired private InventoryService inventoryService;
    @Autowired private CouponBatchRepository couponBatchRepository;
    @Autowired private FuelTypeRepository fuelTypeRepository;
    @Autowired private LocationService locationService;
    @Autowired private DepartmentService departmentService;

    @Test
    void fullPhysicalLifecycleFlow() {
        Long fuelTypeId = fuelTypeRepository.findAll().getFirst().getId();

        List<CouponResponse> generated = couponService.generateBulkCoupons(new GenerateBulkCouponRequest(
                fuelTypeId, BigDecimal.valueOf(5), List.of(new DenominationLine(BigDecimal.ONE, 5)),
                null, null, CouponType.PHYSICAL, LocalDate.now().plusYears(1), "tester"));

        assertThat(generated).hasSize(5).allSatisfy(coupon -> {
            assertThat(coupon.status()).isEqualTo(CouponStatus.GENERATED);
            assertThat(coupon.location().code()).isEqualTo("HQ");
            assertThat(coupon.department().code()).isEqualTo("STOCKS");
            assertThat(coupon.batchNumber()).isNotBlank();
        });

        CouponBatch batch = findBatch(generated.getFirst().batchNumber());
        var receipt = lifecycleService.receiveBatch(batch.getId(), new ReceiveBatchRequest(null, "tester"));
        assertThat(receipt.count()).isEqualTo(5);
        assertThat(receipt.targetStatus()).isEqualTo(CouponStatus.IN_STOCK);

        String cancelledNumber = generated.getFirst().couponNumber();
        lifecycleService.transition(new TransitionRequest(
                List.of(cancelledNumber), CouponStatus.CANCELLED, null, "misprint", "tester"));

        List<CouponMovementResponse> history = lifecycleService.getHistory(cancelledNumber);
        assertThat(history).extracting(CouponMovementResponse::movementType)
                .containsExactly(MovementType.GENERATION, MovementType.RECEIPT, MovementType.CANCELLATION);

        // Terminal state: any further transition must be rejected.
        assertThatThrownBy(() -> lifecycleService.transition(new TransitionRequest(
                List.of(cancelledNumber), CouponStatus.REDEEMED, null, null, "tester")))
                .isInstanceOf(InvalidStatusTransitionException.class);

        List<InventorySummaryResponse> summary = inventoryService.summarize(null, fuelTypeId, null);
        long inStock = summary.stream()
                .filter(row -> row.status() == CouponStatus.IN_STOCK)
                .mapToLong(InventorySummaryResponse::count).sum();
        long cancelled = summary.stream()
                .filter(row -> row.status() == CouponStatus.CANCELLED)
                .mapToLong(InventorySummaryResponse::count).sum();
        assertThat(inStock).isEqualTo(4);
        assertThat(cancelled).isEqualTo(1);
    }

    @Test
    void digitalCouponsSkipTheReceiptStep() {
        Long fuelTypeId = fuelTypeRepository.findAll().getLast().getId();

        List<CouponResponse> generated = couponService.generateBulkCoupons(new GenerateBulkCouponRequest(
                fuelTypeId, BigDecimal.valueOf(2), List.of(new DenominationLine(BigDecimal.ONE, 2)),
                null, null, CouponType.DIGITAL, null, "tester"));

        assertThat(generated).hasSize(2)
                .allSatisfy(coupon -> assertThat(coupon.status()).isEqualTo(CouponStatus.IN_STOCK));

        List<CouponMovementResponse> history = lifecycleService.getHistory(generated.getFirst().couponNumber());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().movementType()).isEqualTo(MovementType.GENERATION);
        assertThat(history.getFirst().toStatus()).isEqualTo(CouponStatus.IN_STOCK);
    }

    @Test
    void transferRangeAndWholeBatchMoveOnlyTheSelectedCouponsAfterApproval() {
        Long fuelTypeId = fuelTypeRepository.findAll().getFirst().getId();
        Long depotId = locationService.create(
                new CreateLocationRequest("DEP-TR", "Transfer Test Depot", LocationType.DEPOT, null)).id();
        Long commercialId = departmentService.create(
                new CreateDepartmentRequest("COMM-TR", "Commercial Test")).id();

        List<CouponResponse> generated = couponService.generateBulkCoupons(new GenerateBulkCouponRequest(
                fuelTypeId, BigDecimal.valueOf(5), List.of(new DenominationLine(BigDecimal.ONE, 5)),
                null, null, CouponType.PHYSICAL, null, "tester"));
        CouponBatch batch = findBatch(generated.getFirst().batchNumber());
        lifecycleService.receiveBatch(batch.getId(), new ReceiveBatchRequest(null, "tester"));

        // Transfer coupons #2-#4 (of 5) to the depot + commercial department, keeping status.
        // Moving location/department defers for supervisor approval instead of applying immediately.
        ApprovalRequestResponse rangeRequest = pending(lifecycleService.transferByBatch(new TransferRequest(
                batch.getId(), 2, 4, null, depotId, commercialId, null, null, "tester")));
        assertThat(rangeRequest.status()).isEqualTo(ApprovalStatus.PENDING);

        PagedResponse<CouponResponse> beforeApproval = couponService.getCoupons(
                new CouponFilterRequest(null, null, null, null, depotId, null, null, null), PageRequest.of(0, 20));
        assertThat(beforeApproval.content()).isEmpty();

        ApprovalRequestResponse approvedRange = lifecycleService.approve(rangeRequest.id(),
                new ApprovalDecisionRequest("supervisor", null));
        assertThat(approvedRange.status()).isEqualTo(ApprovalStatus.APPROVED);

        PagedResponse<CouponResponse> atDepot = couponService.getCoupons(
                new CouponFilterRequest(null, null, null, null, depotId, null, null, null), PageRequest.of(0, 20));
        assertThat(atDepot.content()).hasSize(3).allSatisfy(coupon -> {
            assertThat(coupon.location().code()).isEqualTo("DEP-TR");
            assertThat(coupon.department().code()).isEqualTo("COMM-TR");
            assertThat(coupon.status()).isEqualTo(CouponStatus.IN_STOCK);
        });

        PagedResponse<CouponResponse> wholeBatch = couponService.getCoupons(
                new CouponFilterRequest(null, null, null, null, null, null, batch.getId(), null), PageRequest.of(0, 20));
        long stillAtHq = wholeBatch.content().stream().filter(c -> c.location().code().equals("HQ")).count();
        assertThat(stillAtHq).isEqualTo(2);

        // Whole-batch transfer (no range) also defers; approving it moves everything, including the 2 left behind.
        ApprovalRequestResponse wholeRequest = pending(lifecycleService.transferByBatch(new TransferRequest(
                batch.getId(), null, null, null, depotId, null, null, null, "tester")));
        lifecycleService.approve(wholeRequest.id(), new ApprovalDecisionRequest("supervisor", null));

        PagedResponse<CouponResponse> allAtDepot = couponService.getCoupons(
                new CouponFilterRequest(null, null, null, null, depotId, null, null, null), PageRequest.of(0, 20));
        assertThat(allAtDepot.content()).hasSize(5);
    }

    @Test
    void rejectedTransferLeavesCouponsUntouched() {
        Long fuelTypeId = fuelTypeRepository.findAll().getFirst().getId();
        Long depotId = locationService.create(
                new CreateLocationRequest("DEP-RJ", "Reject Test Depot", LocationType.DEPOT, null)).id();

        List<CouponResponse> generated = couponService.generateBulkCoupons(new GenerateBulkCouponRequest(
                fuelTypeId, BigDecimal.ONE, List.of(new DenominationLine(BigDecimal.ONE, 1)),
                null, null, CouponType.DIGITAL, null, "tester"));
        String couponNumber = generated.getFirst().couponNumber();
        CouponBatch batch = findBatch(generated.getFirst().batchNumber());

        ApprovalRequestResponse request = pending(lifecycleService.transferByBatch(new TransferRequest(
                batch.getId(), null, null, null, depotId, null, null, null, "tester")));

        ApprovalRequestResponse rejected = lifecycleService.reject(request.id(),
                new ApprovalDecisionRequest("supervisor", "wrong destination"));
        assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(rejected.decisionReason()).isEqualTo("wrong destination");

        CouponResponse coupon = couponService.getCouponByNumber(couponNumber);
        assertThat(coupon.location().code()).isEqualTo("HQ");

        List<CouponMovementResponse> history = lifecycleService.getHistory(couponNumber);
        assertThat(history).extracting(CouponMovementResponse::movementType)
                .containsExactly(MovementType.GENERATION);
    }

    private CouponBatch findBatch(String batchNumber) {
        return couponBatchRepository.findAll().stream()
                .filter(b -> b.getBatchNumber().equals(batchNumber))
                .findFirst()
                .orElseThrow();
    }

    private static <T> ApprovalRequestResponse pending(ActionOutcome<T> outcome) {
        return switch (outcome) {
            case ActionOutcome.Pending<T> pending -> pending.request();
            case ActionOutcome.Applied<T> applied -> throw new AssertionError("Expected Pending but was Applied: " + applied.result());
        };
    }
}