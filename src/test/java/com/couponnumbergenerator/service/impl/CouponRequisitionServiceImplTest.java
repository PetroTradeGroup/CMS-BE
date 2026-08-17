package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.CreateRequisitionRequest;
import com.couponnumbergenerator.dto.request.DenominationLine;
import com.couponnumbergenerator.dto.request.FulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.RequisitionDecisionRequest;
import com.couponnumbergenerator.dto.request.RequisitionLineRequest;
import com.couponnumbergenerator.dto.request.TransferRequest;
import com.couponnumbergenerator.dto.response.RequisitionResponse;
import com.couponnumbergenerator.dto.response.TransferResultResponse;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.RequisitionStatus;
import com.couponnumbergenerator.exception.CouponBatchNotFoundException;
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
import com.couponnumbergenerator.repository.CouponRequisitionRepository;
import com.couponnumbergenerator.repository.DepartmentRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.ActionOutcome;
import com.couponnumbergenerator.service.CouponLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponRequisitionServiceImplTest {

    @Mock private CouponRequisitionRepository couponRequisitionRepository;
    @Mock private CouponApprovalRequestRepository couponApprovalRequestRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private FuelTypeRepository fuelTypeRepository;
    @Mock private CouponBatchRepository couponBatchRepository;
    @Mock private CouponLifecycleService couponLifecycleService;

    @InjectMocks
    private CouponRequisitionServiceImpl service;

    private Department commercial;
    private Location hq;
    private FuelType petrol;
    private FuelType diesel;

    @BeforeEach
    void setUp() {
        commercial = Department.builder().id(2L).code("COMMERCIAL").name("Commercial").build();
        hq = Location.builder().id(1L).code("HQ").name("Head Office").build();
        petrol = FuelType.builder().id(1L).name("Petrol").typeCode("PU").active(true).build();
        diesel = FuelType.builder().id(2L).name("Diesel").typeCode("DU").active(true).build();
        // Most fulfill() tests draw from a petrol batch — lenient so tests that don't reach the
        // fuel-type-mismatch check aren't flagged for an unused stub.
        lenient().when(couponBatchRepository.findById(10L))
                .thenReturn(Optional.of(CouponBatch.builder().id(10L).batchNumber("BAT-010").fuelType(petrol).build()));
        lenient().when(couponBatchRepository.findById(11L))
                .thenReturn(Optional.of(CouponBatch.builder().id(11L).batchNumber("BAT-011").fuelType(petrol).build()));
    }

    private CouponRequisition requisition(Long id, RequisitionStatus status, RequisitionLine... lines) {
        CouponRequisition requisition = CouponRequisition.builder()
                .id(id).requestingDepartment(commercial).location(hq)
                .requestedBy("commercial-clerk").status(status).build();
        for (RequisitionLine line : lines) {
            requisition.addLine(line);
        }
        return requisition;
    }

    private RequisitionLine line(FuelType fuelType, BigDecimal denomination, String requestedLitres, String fulfilledLitres) {
        return RequisitionLine.builder()
                .fuelType(fuelType)
                .denomination(denomination)
                .requestedLitres(new BigDecimal(requestedLitres))
                .fulfilledLitres(new BigDecimal(fulfilledLitres))
                .build();
    }

    @Test
    void createSavesARequisitionWithItsLines() {
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(commercial));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(hq));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(fuelTypeRepository.findById(2L)).thenReturn(Optional.of(diesel));
        when(couponRequisitionRepository.save(any())).thenAnswer(invocation -> {
            CouponRequisition saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        // Stocks request in books: 1 book of 20 L = 2,000 L; 2 books of 50 L = 10,000 L.
        RequisitionResponse result = service.create(new CreateRequisitionRequest(2L, 1L, "commercial-clerk",
                List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), null, 1),
                        new RequisitionLineRequest(2L, new BigDecimal("50"), null, 2))));

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.department().code()).isEqualTo("COMMERCIAL");
        assertThat(result.status()).isEqualTo(RequisitionStatus.PENDING);
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().getFirst().fuelType().name()).isEqualTo("Petrol");
        assertThat(result.lines().getFirst().requestedLitres()).isEqualByComparingTo("2000");
        assertThat(result.lines().getFirst().requestedBooks()).isEqualTo(1);
        assertThat(result.lines().getFirst().fulfilledLitres()).isEqualByComparingTo("0");
    }

    @Test
    void createAllowsTheSameDenominationForDifferentFuelTypesOnOneRequisition() {
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(commercial));
        when(locationRepository.findById(1L)).thenReturn(Optional.of(hq));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(fuelTypeRepository.findById(2L)).thenReturn(Optional.of(diesel));
        when(couponRequisitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // A department can ask for both petrol and diesel in one requisition, even at the same denomination.
        RequisitionResponse result = service.create(new CreateRequisitionRequest(2L, 1L, "commercial-clerk",
                List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), null, 1),
                        new RequisitionLineRequest(2L, new BigDecimal("20"), null, 1))));

        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines()).extracting(line -> line.fuelType().name())
                .containsExactlyInAnyOrder("Petrol", "Diesel");
    }

    @Test
    void createRejectsDuplicateDenominationLinesOfTheSameFuelType() {
        assertThatThrownBy(() -> service.create(new CreateRequisitionRequest(2L, 1L, "commercial-clerk",
                List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), new BigDecimal("200")),
                        new RequisitionLineRequest(1L, new BigDecimal("20.00"), new BigDecimal("100"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not repeat");
    }

    @Test
    void fulfillBuildsTheUnderlyingTransferAndLinksTheResultingApproval() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PENDING,
                line(petrol, new BigDecimal("20"), "200", "0"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));

        CouponApprovalRequest approvalStub = CouponApprovalRequest.builder().id(9L).build();
        var pendingResponse = com.couponnumbergenerator.dto.response.ApprovalRequestResponse.from(approvalStub);
        ActionOutcome<TransferResultResponse> outcome = new ActionOutcome.Pending<>(pendingResponse);
        when(couponLifecycleService.transferByBatch(any(), eq(true))).thenReturn(outcome);
        CouponApprovalRequest approval = CouponApprovalRequest.builder().id(9L).build();
        when(couponApprovalRequestRepository.findById(9L)).thenReturn(Optional.of(approval));

        ActionOutcome<TransferResultResponse> result = service.fulfill(5L, new FulfillRequisitionRequest(
                10L, List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), new BigDecimal("100"))),
                CouponStatus.ALLOCATED, "issued to Commercial", "stock-clerk"));

        assertThat(result).isSameAs(outcome);
        assertThat(approval.getRequisition()).isSameAs(requisition);

        ArgumentCaptor<TransferRequest> captor = ArgumentCaptor.captor();
        verify(couponLifecycleService).transferByBatch(captor.capture(), eq(true));
        TransferRequest submitted = captor.getValue();
        assertThat(submitted.batchId()).isEqualTo(10L);
        assertThat(submitted.toDepartmentId()).isEqualTo(2L);
        assertThat(submitted.toLocationId()).isEqualTo(1L);
        assertThat(submitted.targetStatus()).isEqualTo(CouponStatus.ALLOCATED);
        // 100 litres of 20L coupons = 5 coupons — the litres-to-count conversion Stock no longer does by hand.
        assertThat(submitted.denominationLines()).containsExactly(new DenominationLine(new BigDecimal("20"), 5));
    }

    @Test
    void fulfillRejectsWhenTheBatchsFuelTypeDoesNotMatchTheLine() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PENDING,
                line(diesel, new BigDecimal("20"), "200", "0"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));
        // Batch 10 (stubbed in setUp) is petrol, but this line is a diesel request.

        assertThatThrownBy(() -> service.fulfill(5L, new FulfillRequisitionRequest(
                10L, List.of(new RequisitionLineRequest(2L, new BigDecimal("20"), new BigDecimal("100"))),
                null, null, "stock-clerk")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Petrol")
                .hasMessageContaining("Diesel");
        verify(couponLifecycleService, never()).transferByBatch(any(), anyBoolean());
    }

    @Test
    void fulfillFailsForAnUnknownBatch() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PENDING,
                line(petrol, new BigDecimal("20"), "200", "0"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));
        when(couponBatchRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fulfill(5L, new FulfillRequisitionRequest(
                999L, List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), new BigDecimal("100"))),
                null, null, "stock-clerk")))
                .isInstanceOf(CouponBatchNotFoundException.class);
    }

    @Test
    void fulfillAllowsAPartialDeliveryWithinTheOutstandingBalance() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PENDING,
                line(petrol, new BigDecimal("20"), "200", "160"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));
        lenient().when(couponLifecycleService.transferByBatch(any(), eq(true)))
                .thenReturn(new ActionOutcome.Applied<>(null));

        // Only 40L outstanding (200 requested - 160 already fulfilled) — asking for exactly that is fine.
        service.fulfill(5L, new FulfillRequisitionRequest(
                10L, List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), new BigDecimal("40"))),
                null, null, "stock-clerk"));

        ArgumentCaptor<TransferRequest> captor = ArgumentCaptor.captor();
        verify(couponLifecycleService).transferByBatch(captor.capture(), eq(true));
        assertThat(captor.getValue().denominationLines()).containsExactly(new DenominationLine(new BigDecimal("20"), 2));
    }

    @Test
    void fulfillRejectsALitreAmountExceedingWhatsOutstanding() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PENDING,
                line(petrol, new BigDecimal("20"), "200", "160"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));

        assertThatThrownBy(() -> service.fulfill(5L, new FulfillRequisitionRequest(
                10L, List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), new BigDecimal("60"))),
                null, null, "stock-clerk")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only 40")
                .hasMessageContaining("outstanding");
    }

    @Test
    void fulfillRejectsLitresThatDoNotDivideEvenlyByTheDenomination() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PENDING,
                line(petrol, new BigDecimal("20"), "200", "0"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));

        assertThatThrownBy(() -> service.fulfill(5L, new FulfillRequisitionRequest(
                10L, List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), new BigDecimal("45"))),
                null, null, "stock-clerk")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not divide evenly");
    }

    @Test
    void fulfillRejectsALineForAFuelTypeAndDenominationNotOnTheRequisition() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PENDING,
                line(petrol, new BigDecimal("20"), "200", "0"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));

        assertThatThrownBy(() -> service.fulfill(5L, new FulfillRequisitionRequest(
                10L, List.of(new RequisitionLineRequest(1L, new BigDecimal("50"), new BigDecimal("100"))),
                null, null, "stock-clerk")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no line for fuel type");
    }

    @Test
    void fulfillIsStillAllowedForARequisitionThatIsPartiallyFulfilled() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PARTIALLY_FULFILLED,
                line(petrol, new BigDecimal("20"), "200", "40"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));
        CouponApprovalRequest approvalStub = CouponApprovalRequest.builder().id(9L).build();
        var pendingResponse = com.couponnumbergenerator.dto.response.ApprovalRequestResponse.from(approvalStub);
        when(couponLifecycleService.transferByBatch(any(), eq(true)))
                .thenReturn(new ActionOutcome.Pending<>(pendingResponse));
        when(couponApprovalRequestRepository.findById(9L))
                .thenReturn(Optional.of(CouponApprovalRequest.builder().id(9L).build()));

        // A second batch is being drawn against for the remaining outstanding balance — allowed
        // to proceed even though the requisition already carries a partial delivery.
        service.fulfill(5L, new FulfillRequisitionRequest(
                11L, List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), new BigDecimal("60"))),
                null, null, "stock-clerk"));

        verify(couponLifecycleService).transferByBatch(any(), eq(true));
    }

    @Test
    void fulfillFailsForARequisitionThatIsNotPending() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.FULFILLED,
                line(petrol, new BigDecimal("20"), "200", "200"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));

        assertThatThrownBy(() -> service.fulfill(5L, new FulfillRequisitionRequest(
                10L, List.of(new RequisitionLineRequest(1L, new BigDecimal("20"), new BigDecimal("20"))),
                null, null, "stock-clerk")))
                .isInstanceOf(RequisitionAlreadyDecidedException.class);
    }

    @Test
    void rejectMarksTheRequisitionRejected() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PENDING,
                line(petrol, new BigDecimal("20"), "200", "0"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));

        RequisitionResponse result = service.reject(5L, new RequisitionDecisionRequest("stock-supervisor", "no stock available"));

        assertThat(result.status()).isEqualTo(RequisitionStatus.REJECTED);
        assertThat(result.decisionReason()).isEqualTo("no stock available");
    }

    @Test
    void rejectClosesOutARequisitionThatIsPartiallyFulfilled() {
        CouponRequisition requisition = requisition(5L, RequisitionStatus.PARTIALLY_FULFILLED,
                line(petrol, new BigDecimal("20"), "200", "40"));
        when(couponRequisitionRepository.findById(5L)).thenReturn(Optional.of(requisition));

        // No further stock available — Stock closes out what's left outstanding.
        RequisitionResponse result = service.reject(5L,
                new RequisitionDecisionRequest("stock-supervisor", "no further stock available"));

        assertThat(result.status()).isEqualTo(RequisitionStatus.REJECTED);
    }

    @Test
    void rejectRequiresAReason() {
        assertThatThrownBy(() -> service.reject(5L, new RequisitionDecisionRequest("stock-supervisor", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason is required");
    }

    @Test
    void getRequisitionThrowsWhenUnknown() {
        when(couponRequisitionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRequisition(99L))
                .isInstanceOf(RequisitionNotFoundException.class);
    }
}
