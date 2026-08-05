package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.ApprovalDecisionRequest;
import com.couponnumbergenerator.dto.request.DenominationLine;
import com.couponnumbergenerator.dto.request.ReceiptConfirmationRequest;
import com.couponnumbergenerator.dto.request.ReceiveBatchRequest;
import com.couponnumbergenerator.dto.request.TransferRequest;
import com.couponnumbergenerator.dto.request.TransitionRequest;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.DenominationCountResponse;
import com.couponnumbergenerator.dto.response.TransferResultResponse;
import com.couponnumbergenerator.dto.response.TransferredCouponResponse;
import com.couponnumbergenerator.dto.response.TransitionResultResponse;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.MovementType;
import com.couponnumbergenerator.enums.RequisitionStatus;
import com.couponnumbergenerator.exception.ApprovalAlreadyDecidedException;
import com.couponnumbergenerator.exception.CouponBatchNotFoundException;
import com.couponnumbergenerator.exception.CouponNotFoundException;
import com.couponnumbergenerator.exception.InvalidStatusTransitionException;
import com.couponnumbergenerator.exception.ReceiptNotAwaitedException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.model.CouponMovement;
import com.couponnumbergenerator.model.CouponRequisition;
import com.couponnumbergenerator.model.Department;
import com.couponnumbergenerator.model.FuelType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponLifecycleServiceImplTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponMovementRepository couponMovementRepository;
    @Mock private CouponBatchRepository couponBatchRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private CouponApprovalRequestRepository couponApprovalRequestRepository;
    @Mock private BulkConfigService bulkConfigService;
    @Mock private com.couponnumbergenerator.service.QrCodeService qrCodeService;

    @InjectMocks
    private CouponLifecycleServiceImpl service;

    private Location hq;
    private Location depot;
    private Department stocks;
    private Department commercial;
    private FuelType petrol;

    @BeforeEach
    void setUp() {
        hq = Location.builder().id(1L).code("HQ").name("Head Office").build();
        depot = Location.builder().id(2L).code("DEP1").name("North Depot").build();
        stocks = Department.builder().id(1L).code("STOCKS").name("Stocks").build();
        commercial = Department.builder().id(2L).code("COMMERCIAL").name("Commercial").build();
        petrol = FuelType.builder().id(1L).name("Petrol").typeCode("PU").active(true).build();
        lenient().when(bulkConfigService.getMaxCount()).thenReturn(20_000);
    }

    private Coupon coupon(String number, CouponStatus status) {
        return Coupon.builder().couponNumber(number).status(status).denomination(new BigDecimal("20"))
                .currentLocation(hq).currentDepartment(stocks).build();
    }

    private Coupon coupon(String number, CouponStatus status, int batchSequence) {
        return Coupon.builder().couponNumber(number).status(status).denomination(new BigDecimal("20"))
                .currentLocation(hq).currentDepartment(stocks).batchSequence(batchSequence).build();
    }

    private static <T> T applied(ActionOutcome<T> outcome) {
        return switch (outcome) {
            case ActionOutcome.Applied<T> applied -> applied.result();
            case ActionOutcome.Pending<T> pending -> throw new AssertionError("Expected Applied but was Pending: " + pending.request());
        };
    }

    private static <T> ApprovalRequestResponse pending(ActionOutcome<T> outcome) {
        return switch (outcome) {
            case ActionOutcome.Pending<T> pending -> pending.request();
            case ActionOutcome.Applied<T> applied -> throw new AssertionError("Expected Pending but was Applied: " + applied.result());
        };
    }

    /** Mimics JPA identity generation so saved CouponApprovalRequest instances get an id back. */
    private static Answer<CouponApprovalRequest> savingApproval() {
        return invocation -> {
            CouponApprovalRequest approval = invocation.getArgument(0);
            approval.setId(1L);
            return approval;
        };
    }

    @Test
    void transitionWritesOneMovementPerCoupon() {
        List<Coupon> coupons = List.of(coupon("PU002M0000001", CouponStatus.IN_STOCK),
                coupon("PU002M0000002", CouponStatus.IN_STOCK));
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(coupons);

        TransitionResultResponse result = applied(service.transition(new TransitionRequest(
                List.of("PU002M0000001", "PU002M0000002"), CouponStatus.ALLOCATED, null, null, "tester")));

        assertThat(result.count()).isEqualTo(2);
        assertThat(coupons).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(CouponStatus.ALLOCATED));

        ArgumentCaptor<List<CouponMovement>> captor = ArgumentCaptor.captor();
        verify(couponMovementRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2).allSatisfy(movement -> {
            assertThat(movement.getMovementType()).isEqualTo(MovementType.ALLOCATION);
            assertThat(movement.getFromStatus()).isEqualTo(CouponStatus.IN_STOCK);
            assertThat(movement.getToStatus()).isEqualTo(CouponStatus.ALLOCATED);
            assertThat(movement.getPerformedBy()).isEqualTo("tester");
        });
    }

    @Test
    void illegalTransitionRejectsTheWholeBatch() {
        List<Coupon> coupons = List.of(coupon("PU002M0000001", CouponStatus.IN_STOCK),
                coupon("PU002M0000002", CouponStatus.REDEEMED));
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(coupons);

        assertThatThrownBy(() -> service.transition(new TransitionRequest(
                List.of("PU002M0000001", "PU002M0000002"), CouponStatus.ALLOCATED, null, null, null)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PU002M0000002");

        verify(couponRepository, never()).saveAll(any());
        verify(couponMovementRepository, never()).saveAll(any());
    }

    @Test
    void missingCouponNumbersAreReported() {
        when(couponRepository.findByCouponNumberIn(anyCollection()))
                .thenReturn(List.of(coupon("PU002M0000001", CouponStatus.IN_STOCK)));

        assertThatThrownBy(() -> service.transition(new TransitionRequest(
                List.of("PU002M0000001", "PU002M9999999"), CouponStatus.ALLOCATED, null, null, null)))
                .isInstanceOf(CouponNotFoundException.class)
                .hasMessageContaining("PU002M9999999");
    }

    @Test
    void reasonIsMandatoryForCancellationAndFlagging() {
        assertThatThrownBy(() -> service.transition(new TransitionRequest(
                List.of("PU002M0000001"), CouponStatus.CANCELLED, null, " ", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");

        assertThatThrownBy(() -> service.transition(new TransitionRequest(
                List.of("PU002M0000001"), CouponStatus.FLAGGED, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transitionWithLocationIsDeferredForApproval() {
        Coupon coupon = coupon("PU002M0000001", CouponStatus.IN_STOCK);
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(List.of(coupon));
        when(locationRepository.findById(2L)).thenReturn(Optional.of(depot));
        when(couponApprovalRequestRepository.save(any())).thenAnswer(savingApproval());

        ApprovalRequestResponse result = pending(service.transition(new TransitionRequest(
                List.of("PU002M0000001"), CouponStatus.ALLOCATED, 2L, null, "tester")));

        assertThat(result.requestType()).isEqualTo(ApprovalRequestType.TRANSITION);
        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(result.toLocation().code()).isEqualTo("DEP1");
        assertThat(result.requestedBy()).isEqualTo("tester");
        // This coupon predates batch position tracking (no batchSequence) — left out rather than
        // showing up as a null in the list.
        assertThat(result.batchSequences()).isEmpty();

        // Nothing touched yet — the move waits for approval.
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.IN_STOCK);
        assertThat(coupon.getCurrentLocation()).isEqualTo(hq);
        verify(couponRepository, never()).saveAll(any());
        verify(couponMovementRepository, never()).saveAll(any());
    }

    @Test
    void transitionDeferralFailsFastWhenTransitionIsAlreadyIllegal() {
        Coupon coupon = coupon("PU002M0000001", CouponStatus.REDEEMED);
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(List.of(coupon));

        assertThatThrownBy(() -> service.transition(new TransitionRequest(
                List.of("PU002M0000001"), CouponStatus.ALLOCATED, 2L, null, null)))
                .isInstanceOf(InvalidStatusTransitionException.class);

        verify(couponApprovalRequestRepository, never()).save(any());
    }

    @Test
    void transitionRejectsInTransitAsAClientRequestedTargetStatus() {
        // IN_TRANSIT is set by approve() and cleared by confirmReceipt() — a caller requesting it
        // directly used to sail through validation (IN_STOCK -> IN_TRANSIT is legal) and only blow
        // up later at confirmReceipt as an illegal IN_TRANSIT -> IN_TRANSIT self-transition.
        assertThatThrownBy(() -> service.transition(new TransitionRequest(
                List.of("PU002M0000001"), CouponStatus.IN_TRANSIT, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IN_TRANSIT");

        verifyNoInteractions(couponRepository);
    }

    @Test
    void transferRejectsInTransitAsAClientRequestedTargetStatus() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 2)));

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, null, 2L, null, CouponStatus.IN_TRANSIT, null, "tester")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IN_TRANSIT");
    }

    @Test
    void transitionBatchSizeIsCappedByBulkConfig() {
        when(bulkConfigService.getMaxCount()).thenReturn(1);

        assertThatThrownBy(() -> service.transition(new TransitionRequest(
                List.of("PU002M0000001", "PU002M0000002"), CouponStatus.ALLOCATED, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the configured maximum");
    }

    @Test
    void receiveBatchMovesGeneratedCouponsIntoStockAtTheOrigin() {
        CouponBatch batch = CouponBatch.builder()
                .id(10L).batchNumber("BAT-X").originLocation(depot).build();
        Coupon coupon = coupon("PU002M0000001", CouponStatus.GENERATED);
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch));
        when(couponRepository.findByBatchIdAndStatus(10L, CouponStatus.GENERATED)).thenReturn(List.of(coupon));

        TransitionResultResponse result = service.receiveBatch(10L, ReceiveBatchRequest.empty());

        assertThat(result.count()).isEqualTo(1);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.IN_STOCK);
        assertThat(coupon.getCurrentLocation()).isEqualTo(depot);

        ArgumentCaptor<List<CouponMovement>> captor = ArgumentCaptor.captor();
        verify(couponMovementRepository).saveAll(captor.capture());
        assertThat(captor.getValue().getFirst().getMovementType()).isEqualTo(MovementType.RECEIPT);
    }

    @Test
    void receiveBatchFailsWhenBatchIsUnknownOrEmpty() {
        when(couponBatchRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.receiveBatch(99L, ReceiveBatchRequest.empty()))
                .isInstanceOf(CouponBatchNotFoundException.class);

        CouponBatch batch = CouponBatch.builder()
                .id(10L).batchNumber("BAT-X").originLocation(depot).build();
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch));
        when(couponRepository.findByBatchIdAndStatus(10L, CouponStatus.GENERATED)).thenReturn(List.of());
        assertThatThrownBy(() -> service.receiveBatch(10L, ReceiveBatchRequest.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no coupons awaiting receipt");
    }

    private CouponBatch batch(long id, int quantity) {
        return CouponBatch.builder().id(id).batchNumber("BAT-X").originLocation(hq).quantity(quantity).build();
    }

    @Test
    void transferWholeBatchWithLocationAndDepartmentIsDeferredForApproval() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 2)));
        List<Coupon> coupons = List.of(
                coupon("PU002M0000001", CouponStatus.IN_STOCK, 1),
                coupon("PU002M0000002", CouponStatus.IN_STOCK, 2));
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(10L)).thenReturn(coupons);
        when(locationRepository.findById(2L)).thenReturn(Optional.of(depot));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(commercial));
        when(couponApprovalRequestRepository.save(any())).thenAnswer(savingApproval());

        ApprovalRequestResponse result = pending(service.transferByBatch(new TransferRequest(
                10L, null, null, null, 2L, 2L, null, null, "tester")));

        assertThat(result.requestType()).isEqualTo(ApprovalRequestType.TRANSFER);
        assertThat(result.batchNumber()).isEqualTo("BAT-X");
        assertThat(result.toLocation().code()).isEqualTo("DEP1");
        assertThat(result.toDepartment().code()).isEqualTo("COMMERCIAL");
        assertThat(result.targetStatus()).isNull();
        // A whole-batch selection has no pinned couponNumbers (empty until approve() re-selects),
        // but count/denominations/batchSequences are still known up front — this is exactly the
        // case that used to show 0 / [] / [].
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.denominations()).containsExactly(new DenominationCountResponse(new BigDecimal("20"), 2));
        assertThat(result.batchSequences()).containsExactly(1, 2);
        assertThat(result.couponNumbers()).isEmpty();

        assertThat(coupons).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo(CouponStatus.IN_STOCK);
            assertThat(c.getCurrentLocation()).isEqualTo(hq);
            assertThat(c.getCurrentDepartment()).isEqualTo(stocks);
        });
        verify(couponRepository, never()).saveAll(any());
    }

    @Test
    void transferSubRangeWithLocationIsDeferredForApproval() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 20_000)));
        List<Coupon> rangeCoupons = List.of(
                coupon("PU002M0000010", CouponStatus.IN_STOCK, 10),
                coupon("PU002M0000011", CouponStatus.IN_STOCK, 11));
        when(couponRepository.findByBatchIdAndBatchSequenceBetweenOrderByBatchSequenceAsc(10L, 10, 11))
                .thenReturn(rangeCoupons);
        when(locationRepository.findById(2L)).thenReturn(Optional.of(depot));
        when(couponApprovalRequestRepository.save(any())).thenAnswer(savingApproval());

        ApprovalRequestResponse result = pending(service.transferByBatch(new TransferRequest(
                10L, 10, 11, null, 2L, null, CouponStatus.ALLOCATED, null, "tester")));

        assertThat(result.rangeStart()).isEqualTo(10);
        assertThat(result.rangeEnd()).isEqualTo(11);
        assertThat(result.targetStatus()).isEqualTo(CouponStatus.ALLOCATED);
        assertThat(rangeCoupons).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(CouponStatus.IN_STOCK));
        verify(couponRepository, never()).saveAll(any());
    }

    @Test
    void approveOfAWholeBatchDepartmentHandoffOnlyMovesCouponsToInTransit() {
        CouponBatch batch = batch(10L, 2);
        List<Coupon> coupons = List.of(
                coupon("PU002M0000001", CouponStatus.IN_STOCK, 1),
                coupon("PU002M0000002", CouponStatus.IN_STOCK, 2));
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(7L).requestType(ApprovalRequestType.TRANSFER).couponCount(2)
                .batch(batch).toLocation(depot).toDepartment(commercial)
                .status(ApprovalStatus.PENDING).requestedBy("tester")
                .build();
        when(couponApprovalRequestRepository.findById(7L)).thenReturn(Optional.of(approval));
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(10L)).thenReturn(coupons);

        ApprovalRequestResponse result = service.approve(7L, new ApprovalDecisionRequest("supervisor", null));

        // Stock's sign-off (like signing the GIV) — the coupons are issued but not yet in the
        // receiving department's custody: status flips to IN_TRANSIT, location/department untouched.
        assertThat(result.status()).isEqualTo(ApprovalStatus.TRANSFERSHIPMENT);
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.decidedBy()).isEqualTo("supervisor");
        assertThat(coupons).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo(CouponStatus.IN_TRANSIT);
            assertThat(c.getCurrentLocation()).isEqualTo(hq);
            assertThat(c.getCurrentDepartment()).isEqualTo(stocks);
        });

        ArgumentCaptor<List<CouponMovement>> captor = ArgumentCaptor.captor();
        verify(couponMovementRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(movement -> {
            assertThat(movement.getMovementType()).isEqualTo(MovementType.TRANSFER_OUT);
            assertThat(movement.getFromStatus()).isEqualTo(CouponStatus.IN_STOCK);
            assertThat(movement.getToStatus()).isEqualTo(CouponStatus.IN_TRANSIT);
            assertThat(movement.getPerformedBy()).isEqualTo("tester");
        });
    }

    @Test
    void confirmReceiptAppliesTheLocationAndDepartmentAndReturnsTheCouponsToInStock() {
        List<Coupon> coupons = List.of(
                coupon("PU002M0000001", CouponStatus.IN_TRANSIT, 1),
                coupon("PU002M0000002", CouponStatus.IN_TRANSIT, 2));
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(7L).requestType(ApprovalRequestType.TRANSFER).couponCount(2)
                .couponNumbers(List.of("PU002M0000001", "PU002M0000002"))
                .toLocation(depot).toDepartment(commercial)
                .status(ApprovalStatus.TRANSFERSHIPMENT).requestedBy("tester")
                .build();
        when(couponApprovalRequestRepository.findById(7L)).thenReturn(Optional.of(approval));
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(coupons);

        ApprovalRequestResponse result = service.confirmReceipt(7L, new ReceiptConfirmationRequest("commercial-clerk", null));

        assertThat(result.status()).isEqualTo(ApprovalStatus.TRANSRECEIPT);
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.receivedBy()).isEqualTo("commercial-clerk");
        assertThat(result.receivedAt()).isNotNull();
        assertThat(coupons).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo(CouponStatus.IN_STOCK);
            assertThat(c.getCurrentLocation()).isEqualTo(depot);
            assertThat(c.getCurrentDepartment()).isEqualTo(commercial);
        });

        ArgumentCaptor<List<CouponMovement>> captor = ArgumentCaptor.captor();
        verify(couponMovementRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(movement -> {
            assertThat(movement.getMovementType()).isEqualTo(MovementType.TRANSFER_IN);
            assertThat(movement.getFromStatus()).isEqualTo(CouponStatus.IN_TRANSIT);
            assertThat(movement.getToStatus()).isEqualTo(CouponStatus.IN_STOCK);
        });
    }

    @Test
    void confirmReceiptRejectsAnApprovalThatIsNotInTransit() {
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(20L).status(ApprovalStatus.PENDING).build();
        when(couponApprovalRequestRepository.findById(20L)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.confirmReceipt(20L, new ReceiptConfirmationRequest("commercial-clerk", null)))
                .isInstanceOf(ReceiptNotAwaitedException.class);

        verify(couponRepository, never()).findByCouponNumberIn(any());
    }

    @Test
    void confirmReceiptOfALinkedRequisitionCreditsDeliveredLitresAndFlipsToPartiallyFulfilled() {
        RequisitionLine line = RequisitionLine.builder()
                .fuelType(petrol)
                .denomination(new BigDecimal("20"))
                .requestedLitres(new BigDecimal("100"))
                .fulfilledLitres(BigDecimal.ZERO)
                .build();
        CouponRequisition requisition = CouponRequisition.builder().id(5L).status(RequisitionStatus.PENDING).build();
        requisition.addLine(line);

        // Batch 2 only had 20L of eligible stock (2 coupons) against the 100L (5 coupons) requested.
        List<Coupon> coupons = List.of(
                denominatedCoupon("PU006M0000011", CouponStatus.IN_TRANSIT, 11, "20", petrol),
                denominatedCoupon("PU006M0000012", CouponStatus.IN_TRANSIT, 12, "20", petrol));
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(9L).requestType(ApprovalRequestType.TRANSFER)
                .couponNumbers(List.of("PU006M0000011", "PU006M0000012"))
                .status(ApprovalStatus.TRANSFERSHIPMENT).requestedBy("stock-clerk")
                .requisition(requisition)
                .build();
        when(couponApprovalRequestRepository.findById(9L)).thenReturn(Optional.of(approval));
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(coupons);

        service.confirmReceipt(9L, new ReceiptConfirmationRequest("commercial-clerk", null));

        assertThat(line.getFulfilledLitres()).isEqualByComparingTo("40");
        assertThat(line.outstandingLitres()).isEqualByComparingTo("60");
        assertThat(requisition.getStatus()).isEqualTo(RequisitionStatus.PARTIALLY_FULFILLED);
    }

    @Test
    void confirmReceiptOfALinkedRequisitionFlipsToFulfilledOnceEveryLineIsSatisfied() {
        RequisitionLine line = RequisitionLine.builder()
                .fuelType(petrol)
                .denomination(new BigDecimal("20"))
                .requestedLitres(new BigDecimal("40"))
                .fulfilledLitres(BigDecimal.ZERO)
                .build();
        CouponRequisition requisition = CouponRequisition.builder().id(5L).status(RequisitionStatus.PENDING).build();
        requisition.addLine(line);

        List<Coupon> coupons = List.of(
                denominatedCoupon("PU006M0000011", CouponStatus.IN_TRANSIT, 11, "20", petrol),
                denominatedCoupon("PU006M0000012", CouponStatus.IN_TRANSIT, 12, "20", petrol));
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(9L).requestType(ApprovalRequestType.TRANSFER)
                .couponNumbers(List.of("PU006M0000011", "PU006M0000012"))
                .status(ApprovalStatus.TRANSFERSHIPMENT).requestedBy("stock-clerk")
                .requisition(requisition)
                .build();
        when(couponApprovalRequestRepository.findById(9L)).thenReturn(Optional.of(approval));
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(coupons);

        service.confirmReceipt(9L, new ReceiptConfirmationRequest("commercial-clerk", null));

        assertThat(requisition.getStatus()).isEqualTo(RequisitionStatus.FULFILLED);
    }

    @Test
    void approveExecutesThePendingRangeTransferWithStatusChange() {
        CouponBatch batch = batch(10L, 20_000);
        List<Coupon> rangeCoupons = List.of(
                coupon("PU002M0000010", CouponStatus.IN_STOCK, 10),
                coupon("PU002M0000011", CouponStatus.IN_STOCK, 11));
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(8L).requestType(ApprovalRequestType.TRANSFER)
                .batch(batch).rangeStart(10).rangeEnd(11)
                .targetStatus(CouponStatus.IN_TRANSIT).toLocation(depot)
                .status(ApprovalStatus.PENDING).build();
        when(couponApprovalRequestRepository.findById(8L)).thenReturn(Optional.of(approval));
        when(couponRepository.findByBatchIdAndBatchSequenceBetweenOrderByBatchSequenceAsc(10L, 10, 11))
                .thenReturn(rangeCoupons);

        service.approve(8L, new ApprovalDecisionRequest("supervisor", null));

        assertThat(rangeCoupons).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(CouponStatus.IN_TRANSIT));
        ArgumentCaptor<List<CouponMovement>> captor = ArgumentCaptor.captor();
        verify(couponMovementRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).allSatisfy(movement ->
                assertThat(movement.getMovementType()).isEqualTo(MovementType.TRANSFER_OUT));
    }

    @Test
    void approveFailsForAlreadyDecidedRequest() {
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(9L).status(ApprovalStatus.APPROVED).build();
        when(couponApprovalRequestRepository.findById(9L)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> service.approve(9L, new ApprovalDecisionRequest("supervisor", null)))
                .isInstanceOf(ApprovalAlreadyDecidedException.class);
    }

    @Test
    void approveReRunsEligibilityAndFailsIfCouponDriftedSinceRequest() {
        Coupon coupon = coupon("PU002M0000001", CouponStatus.REDEEMED);
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(11L).requestType(ApprovalRequestType.TRANSITION)
                .couponNumbers(List.of("PU002M0000001"))
                .targetStatus(CouponStatus.ALLOCATED).toLocation(depot)
                .status(ApprovalStatus.PENDING).build();
        when(couponApprovalRequestRepository.findById(11L)).thenReturn(Optional.of(approval));
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(List.of(coupon));

        assertThatThrownBy(() -> service.approve(11L, new ApprovalDecisionRequest("supervisor", null)))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void rejectMarksRejectedAndTouchesNoCoupons() {
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(12L).requestType(ApprovalRequestType.TRANSITION)
                .couponNumbers(List.of("PU002M0000001"))
                .status(ApprovalStatus.PENDING).build();
        when(couponApprovalRequestRepository.findById(12L)).thenReturn(Optional.of(approval));

        ApprovalRequestResponse result = service.reject(12L, new ApprovalDecisionRequest("supervisor", "wrong destination"));

        assertThat(result.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(result.decisionReason()).isEqualTo("wrong destination");
        verify(couponRepository, never()).findByCouponNumberIn(any());
        verify(couponMovementRepository, never()).saveAll(any());
    }

    @Test
    void rejectRequiresAReason() {
        assertThatThrownBy(() -> service.reject(13L, new ApprovalDecisionRequest("supervisor", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
        verifyNoInteractions(couponApprovalRequestRepository);
    }

    @Test
    void transferRejectsAsymmetricRangeBounds() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, 5, null, null, 2L, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both be provided, or both omitted");
    }

    @Test
    void transferRejectsInvertedOrTooSmallRange() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, 20, 10, null, 2L, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid range");
    }

    @Test
    void transferRejectsRangeBeyondBatchQuantity() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, 1, 200, null, 2L, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds batch quantity");
    }

    @Test
    void transferRequiresAtLeastOneTargetField() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one of");
    }

    @Test
    void transferBatchNotFoundIsReported() {
        when(couponBatchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                99L, null, null, null, 2L, null, null, null, null)))
                .isInstanceOf(CouponBatchNotFoundException.class);
    }

    @Test
    void transferMixedEligibilityRejectsTheWholeRange() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 2)));
        List<Coupon> coupons = List.of(
                coupon("PU002M0000001", CouponStatus.IN_STOCK, 1),
                coupon("PU002M0000002", CouponStatus.REDEEMED, 2));
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(10L)).thenReturn(coupons);

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, null, null, null, CouponStatus.ALLOCATED, null, null)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PU002M0000002");

        verify(couponRepository, never()).saveAll(any());
        verify(couponMovementRepository, never()).saveAll(any());
    }

    @Test
    void pureReassignmentRejectsACouponThatHasAlreadyBeenAllocated() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 2)));
        List<Coupon> coupons = List.of(
                coupon("PU002M0000001", CouponStatus.IN_STOCK, 1),
                coupon("PU002M0000002", CouponStatus.ALLOCATED, 2));
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(10L)).thenReturn(coupons);

        // No targetStatus — a pure location/department move. Once a coupon is out (ALLOCATED),
        // it can no longer be silently relocated; the whole batch is rejected, all-or-nothing.
        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, null, null, 2L, null, null, "tester")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PU002M0000002")
                .hasMessageContaining("cannot be transferred while ALLOCATED");

        verify(couponApprovalRequestRepository, never()).save(any());
    }

    @Test
    void pureReassignmentRejectsACouponThatIsInStockButOutsideTheStocksDepartment() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 1)));
        // IN_STOCK, but already moved into Commercial — not eligible for a plain relocation.
        Coupon coupon = Coupon.builder().couponNumber("PU002M0000001").status(CouponStatus.IN_STOCK)
                .currentLocation(hq).currentDepartment(commercial).batchSequence(1).build();
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(10L)).thenReturn(List.of(coupon));

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, null, null, 2L, null, null, "tester")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PU002M0000001")
                .hasMessageContaining("COMMERCIAL")
                .hasMessageContaining("STOCKS");

        verify(couponApprovalRequestRepository, never()).save(any());
    }

    @Test
    void statusChangingDeferredTransferRejectsAnIneligibleCouponBeforeReachingTheApprovalQueue() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 2)));
        List<Coupon> coupons = List.of(
                coupon("PU002M0000001", CouponStatus.IN_STOCK, 1),
                // Already past ALLOCATED (redeemed) — can't be allocated again.
                coupon("PU002M0000002", CouponStatus.REDEEMED, 2));
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(10L)).thenReturn(coupons);

        // targetStatus + toDepartmentId would normally defer to the approval queue — but the
        // legality dry-run must reject the whole request first, before any approval record is created.
        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, null, null, 2L, CouponStatus.ALLOCATED, null, "tester")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PU002M0000002")
                .hasMessageContaining("REDEEMED");

        verify(couponApprovalRequestRepository, never()).save(any());
    }

    @Test
    void pureReassignmentRejectsACouponThatIsAlreadyMidTransfer() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 1)));
        List<Coupon> coupons = List.of(coupon("PU002M0000001", CouponStatus.IN_TRANSIT, 1));
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(10L)).thenReturn(coupons);

        // A coupon already IN_TRANSIT can't be transferred again — it has to be received back
        // into stock first. The error names the coupon's actual current status.
        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, null, null, 2L, null, null, "tester")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("PU002M0000001")
                .hasMessageContaining("cannot be transferred while IN_TRANSIT");

        verify(couponApprovalRequestRepository, never()).save(any());
    }

    @Test
    void transferByDenominationForPureReassignmentSkipsCouponsAlreadyOutOfStock() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));
        Coupon alreadyAllocated = denominatedCoupon("PU006M0000012", CouponStatus.ALLOCATED, 12, "20");
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("20")))
                .thenReturn(List.of(
                        denominatedCoupon("PU006M0000011", CouponStatus.IN_STOCK, 11, "20"),
                        alreadyAllocated,
                        denominatedCoupon("PU006M0000013", CouponStatus.IN_STOCK, 13, "20")));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(commercial));
        when(couponApprovalRequestRepository.save(any())).thenAnswer(savingApproval());

        // Pure reassignment (no targetStatus) — an already-allocated coupon is skipped in favor of
        // the next one still in stock, so a repeat "give out N more" call keeps finding fresh coupons.
        ApprovalRequestResponse result = pending(service.transferByBatch(new TransferRequest(
                10L, null, null, List.of(new DenominationLine(new BigDecimal("20"), 2)),
                null, 2L, null, null, "tester")));

        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        ArgumentCaptor<CouponApprovalRequest> captor = ArgumentCaptor.captor();
        verify(couponApprovalRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getCouponNumbers()).containsExactly("PU006M0000011", "PU006M0000013");
        assertThat(alreadyAllocated.getStatus()).isEqualTo(CouponStatus.ALLOCATED);
    }

    @Test
    void approveDetectsACouponAllocatedElsewhereBeforeAPureReassignmentIsApproved() {
        CouponBatch batch = batch(10L, 1);
        // Drifted to ALLOCATED between the transfer request and the supervisor's decision.
        Coupon drifted = coupon("PU002M0000001", CouponStatus.ALLOCATED, 1);
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(15L).requestType(ApprovalRequestType.TRANSFER)
                .batch(batch).toDepartment(commercial)
                .status(ApprovalStatus.PENDING).build();
        when(couponApprovalRequestRepository.findById(15L)).thenReturn(Optional.of(approval));
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(10L)).thenReturn(List.of(drifted));

        assertThatThrownBy(() -> service.approve(15L, new ApprovalDecisionRequest("supervisor", null)))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("cannot be transferred while ALLOCATED");

        verify(couponRepository, never()).saveAll(any());
    }

    @Test
    void transferOnEmptySelectionIsReported() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));
        when(couponRepository.findByBatchIdAndBatchSequenceBetweenOrderByBatchSequenceAsc(10L, 1, 10))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, 1, 10, null, 2L, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No coupons found");
    }

    private Coupon denominatedCoupon(String number, CouponStatus status, int batchSequence, String denomination) {
        return Coupon.builder().couponNumber(number).status(status).denomination(new BigDecimal(denomination))
                .currentLocation(hq).currentDepartment(stocks).batchSequence(batchSequence).build();
    }

    private Coupon denominatedCoupon(String number, CouponStatus status, int batchSequence, String denomination, FuelType fuelType) {
        return Coupon.builder().couponNumber(number).status(status).denomination(new BigDecimal(denomination))
                .fuelType(fuelType).currentLocation(hq).currentDepartment(stocks).batchSequence(batchSequence).build();
    }

    @Test
    void transferByDenominationPicksFirstEligibleCouponsInBatchOrder() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));
        Coupon skipped = denominatedCoupon("PU006M0000012", CouponStatus.REDEEMED, 12, "20");
        Coupon leftover = denominatedCoupon("PU006M0000015", CouponStatus.IN_STOCK, 15, "20");
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("20")))
                .thenReturn(List.of(
                        denominatedCoupon("PU006M0000011", CouponStatus.IN_STOCK, 11, "20"),
                        skipped,
                        denominatedCoupon("PU006M0000013", CouponStatus.IN_STOCK, 13, "20"),
                        denominatedCoupon("PU006M0000014", CouponStatus.IN_STOCK, 14, "20"),
                        leftover));

        TransferResultResponse result = applied(service.transferByBatch(new TransferRequest(
                10L, null, null, List.of(new DenominationLine(new BigDecimal("20"), 3)),
                null, null, CouponStatus.ALLOCATED, null, "tester")));

        assertThat(result.count()).isEqualTo(3);
        assertThat(result.batchNumber()).isEqualTo("BAT-X");
        assertThat(result.denominationLines()).containsExactly(new DenominationLine(new BigDecimal("20"), 3));
        // The ineligible coupon is skipped, and the fourth eligible one is left untouched.
        assertThat(skipped.getStatus()).isEqualTo(CouponStatus.REDEEMED);
        assertThat(leftover.getStatus()).isEqualTo(CouponStatus.IN_STOCK);

        // Full detail of what actually moved — coupon number + its position in the batch.
        assertThat(result.coupons()).extracting(TransferredCouponResponse::couponNumber, TransferredCouponResponse::batchSequence)
                .containsExactly(tuple("PU006M0000011", 11), tuple("PU006M0000013", 13), tuple("PU006M0000014", 14));

        ArgumentCaptor<List<Coupon>> captor = ArgumentCaptor.captor();
        verify(couponRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Coupon::getCouponNumber)
                .containsExactly("PU006M0000011", "PU006M0000013", "PU006M0000014");
        assertThat(captor.getValue()).allSatisfy(c -> assertThat(c.getStatus()).isEqualTo(CouponStatus.ALLOCATED));
    }

    @Test
    void transferByMultipleDenominationLinesCombinesEachLinesSelection() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("20")))
                .thenReturn(List.of(
                        denominatedCoupon("PU006M0000011", CouponStatus.IN_STOCK, 11, "20"),
                        denominatedCoupon("PU006M0000012", CouponStatus.IN_STOCK, 12, "20")));
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("50")))
                .thenReturn(List.of(
                        denominatedCoupon("PU006M0000021", CouponStatus.IN_STOCK, 21, "50"),
                        denominatedCoupon("PU006M0000022", CouponStatus.IN_STOCK, 22, "50"),
                        denominatedCoupon("PU006M0000023", CouponStatus.IN_STOCK, 23, "50")));

        // 5 x 20L + 3 x 50L in a single call.
        TransferResultResponse result = applied(service.transferByBatch(new TransferRequest(
                10L, null, null, List.of(
                        new DenominationLine(new BigDecimal("20"), 2),
                        new DenominationLine(new BigDecimal("50"), 3)),
                null, null, CouponStatus.ALLOCATED, null, "tester")));

        assertThat(result.count()).isEqualTo(5);
        ArgumentCaptor<List<Coupon>> captor = ArgumentCaptor.captor();
        verify(couponRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(Coupon::getCouponNumber).containsExactly(
                "PU006M0000011", "PU006M0000012", "PU006M0000021", "PU006M0000022", "PU006M0000023");
    }

    @Test
    void transferByDenominationWithDepartmentIsDeferredAndPinsCouponNumbers() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("20")))
                .thenReturn(List.of(
                        denominatedCoupon("PU006M0000011", CouponStatus.IN_STOCK, 11, "20"),
                        denominatedCoupon("PU006M0000012", CouponStatus.REDEEMED, 12, "20"),
                        denominatedCoupon("PU006M0000013", CouponStatus.IN_STOCK, 13, "20")));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(commercial));
        when(couponApprovalRequestRepository.save(any())).thenAnswer(savingApproval());

        ApprovalRequestResponse result = pending(service.transferByBatch(new TransferRequest(
                10L, null, null, List.of(new DenominationLine(new BigDecimal("20"), 2)),
                null, 2L, null, "5 x 20L diesel for Operations", "tester")));

        assertThat(result.requestType()).isEqualTo(ApprovalRequestType.TRANSFER);
        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.denominations()).containsExactly(
                new DenominationCountResponse(new BigDecimal("20"), 2));

        // The terminal-status coupon is skipped; the two eligible coupons found are pinned so
        // approval moves precisely these, not a re-run of the selection.
        ArgumentCaptor<CouponApprovalRequest> captor = ArgumentCaptor.captor();
        verify(couponApprovalRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getCouponNumbers())
                .containsExactly("PU006M0000011", "PU006M0000013");
        verify(couponRepository, never()).saveAll(any());
    }

    @Test
    void transferByMultipleDenominationLinesWithDepartmentBreaksDownEachDenomination() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("20")))
                .thenReturn(List.of(
                        denominatedCoupon("PU006M0000011", CouponStatus.IN_STOCK, 11, "20"),
                        denominatedCoupon("PU006M0000012", CouponStatus.IN_STOCK, 12, "20")));
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("50")))
                .thenReturn(List.of(denominatedCoupon("PU006M0000021", CouponStatus.IN_STOCK, 21, "50")));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(commercial));
        when(couponApprovalRequestRepository.save(any())).thenAnswer(savingApproval());

        ApprovalRequestResponse result = pending(service.transferByBatch(new TransferRequest(
                10L, null, null, List.of(
                        new DenominationLine(new BigDecimal("20"), 2),
                        new DenominationLine(new BigDecimal("50"), 1)),
                null, 2L, null, "5 x 20L + 1 x 50L diesel for Operations", "tester")));

        assertThat(result.count()).isEqualTo(3);
        assertThat(result.denominations()).containsExactly(
                new DenominationCountResponse(new BigDecimal("20"), 2),
                new DenominationCountResponse(new BigDecimal("50"), 1));
        assertThat(result.batchSequences()).containsExactly(11, 12, 21);
    }

    @Test
    void transferByDenominationFailsWhenTheBatchCannotSupplyTheQuantity() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("20")))
                .thenReturn(List.of(
                        denominatedCoupon("PU006M0000011", CouponStatus.IN_STOCK, 11, "20"),
                        denominatedCoupon("PU006M0000012", CouponStatus.REDEEMED, 12, "20")));

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, List.of(new DenominationLine(new BigDecimal("20"), 5)),
                null, 2L, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 1 eligible coupon(s)");
    }

    @Test
    void transferByDenominationWithAllowPartialTakesWhateverIsEligibleInstead() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("20")))
                .thenReturn(List.of(
                        denominatedCoupon("PU006M0000011", CouponStatus.IN_STOCK, 11, "20"),
                        denominatedCoupon("PU006M0000012", CouponStatus.REDEEMED, 12, "20")));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(commercial));
        when(couponApprovalRequestRepository.save(any())).thenAnswer(savingApproval());

        // 5 requested, only 1 eligible in this batch — with partial allowed, that's not an error.
        ApprovalRequestResponse result = pending(service.transferByBatch(new TransferRequest(
                10L, null, null, List.of(new DenominationLine(new BigDecimal("20"), 5)),
                null, 2L, null, null, null), true));

        assertThat(result.status()).isEqualTo(ApprovalStatus.PENDING);
        ArgumentCaptor<CouponApprovalRequest> captor = ArgumentCaptor.captor();
        verify(couponApprovalRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getCouponNumbers()).containsExactly("PU006M0000011");
    }

    @Test
    void transferByDenominationWithAllowPartialStillFailsWhenNothingIsEligible() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));
        when(couponRepository.findByBatchIdAndDenominationOrderByBatchSequenceAsc(10L, new BigDecimal("5")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, List.of(new DenominationLine(new BigDecimal("5"), 20)),
                null, 2L, null, null, null), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no eligible coupons");
        verify(couponApprovalRequestRepository, never()).save(any());
    }

    @Test
    void transferRejectsDuplicateDenominationLines() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, null, null, List.of(
                        new DenominationLine(new BigDecimal("20"), 2),
                        new DenominationLine(new BigDecimal("20.00"), 3)),
                null, 2L, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not repeat");
    }

    @Test
    void transferRejectsCombiningRangeAndDenominationSelection() {
        when(couponBatchRepository.findById(10L)).thenReturn(Optional.of(batch(10L, 100)));

        assertThatThrownBy(() -> service.transferByBatch(new TransferRequest(
                10L, 1, 10, List.of(new DenominationLine(new BigDecimal("20"), 5)),
                null, 2L, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not both");
    }

    @Test
    void approveOfAPinnedDenominationTransferOnlyMovesCouponsToInTransit() {
        List<Coupon> pinned = List.of(
                denominatedCoupon("PU006M0000011", CouponStatus.IN_STOCK, 11, "20"),
                denominatedCoupon("PU006M0000013", CouponStatus.IN_STOCK, 13, "20"));
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(14L).requestType(ApprovalRequestType.TRANSFER)
                .batch(batch(10L, 100))
                .couponNumbers(List.of("PU006M0000011", "PU006M0000013"))
                .toDepartment(commercial)
                .status(ApprovalStatus.PENDING).requestedBy("tester")
                .build();
        when(couponApprovalRequestRepository.findById(14L)).thenReturn(Optional.of(approval));
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(pinned);

        ApprovalRequestResponse result = service.approve(14L, new ApprovalDecisionRequest("supervisor", null));

        assertThat(result.status()).isEqualTo(ApprovalStatus.TRANSFERSHIPMENT);
        assertThat(pinned).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo(CouponStatus.IN_TRANSIT);
            assertThat(c.getCurrentDepartment()).isEqualTo(stocks);
        });
        // Pinned coupons are loaded by number — the range selector must not run.
        verify(couponRepository, never()).findByBatchIdOrderByBatchSequenceAsc(any());

        // Full detail of what the approval actually did (issued, not yet received), batch position included.
        assertThat(result.transferredCoupons())
                .extracting(TransferredCouponResponse::couponNumber, TransferredCouponResponse::batchSequence)
                .containsExactly(tuple("PU006M0000011", 11), tuple("PU006M0000013", 13));
    }

    @Test
    void confirmReceiptOfAPinnedDenominationTransferAppliesTheDepartmentMove() {
        List<Coupon> pinned = List.of(
                denominatedCoupon("PU006M0000011", CouponStatus.IN_TRANSIT, 11, "20"),
                denominatedCoupon("PU006M0000013", CouponStatus.IN_TRANSIT, 13, "20"));
        CouponApprovalRequest approval = CouponApprovalRequest.builder()
                .id(14L).requestType(ApprovalRequestType.TRANSFER)
                .couponNumbers(List.of("PU006M0000011", "PU006M0000013"))
                .toDepartment(commercial)
                .status(ApprovalStatus.TRANSFERSHIPMENT).requestedBy("tester")
                .build();
        when(couponApprovalRequestRepository.findById(14L)).thenReturn(Optional.of(approval));
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(pinned);

        ApprovalRequestResponse result = service.confirmReceipt(14L, new ReceiptConfirmationRequest("commercial-clerk", null));

        assertThat(result.status()).isEqualTo(ApprovalStatus.TRANSRECEIPT);
        assertThat(pinned).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo(CouponStatus.IN_STOCK);
            assertThat(c.getCurrentDepartment()).isEqualTo(commercial);
        });
    }
}