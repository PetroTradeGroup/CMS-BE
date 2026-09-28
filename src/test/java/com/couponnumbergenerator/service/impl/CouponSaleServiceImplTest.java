package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.ErpSaleRequest;
import com.couponnumbergenerator.dto.response.CouponSaleResponse;
import com.couponnumbergenerator.enums.SaleLineStatus;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.event.SaleAssignedEvent;
import com.couponnumbergenerator.exception.CouponSaleNotFoundException;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.model.CouponSale;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.CouponSaleRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.CouponLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponSaleServiceImplTest {

    @Mock private CouponSaleRepository couponSaleRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private FuelTypeRepository fuelTypeRepository;
    @Mock private CouponLifecycleService couponLifecycleService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TransactionOperations assignmentTransaction;

    @InjectMocks
    private CouponSaleServiceImpl service;

    private Location siteA;
    private FuelType petrol;
    private FuelType diesel;

    private static final BigDecimal D20 = new BigDecimal("20.00");
    private static final BigDecimal D50 = new BigDecimal("50.00");

    @BeforeEach
    void setUp() {
        siteA = Location.builder().id(1L).code("SITE-A").name("Site A").build();
        petrol = FuelType.builder().id(1L).name("Petrol").typeCode("PU").active(true).build();
        diesel = FuelType.builder().id(2L).name("Diesel").typeCode("DU").active(true).build();
        // Default: the retry wrapper runs its callback straight through, once. doAnswer form
        // so re-stubbing execute(...) in a test doesn't fire this answer during stub setup.
        lenient().doAnswer(CouponSaleServiceImplTest::runCallback)
                .when(assignmentTransaction).execute(any());
    }

    private static Object runCallback(org.mockito.invocation.InvocationOnMock invocation) {
        TransactionCallback<?> callback = invocation.getArgument(0);
        return callback.doInTransaction(mock(TransactionStatus.class));
    }

    private ErpSaleRequest.Line line(Long fuelTypeId, BigDecimal denomination, int quantity) {
        return new ErpSaleRequest.Line(fuelTypeId, denomination, quantity, null, null);
    }

    private ErpSaleRequest saleRequest(String docNumber, ErpSaleRequest.Line... lines) {
        return new ErpSaleRequest(docNumber, "SITE-A", List.of(lines), "cust-ref");
    }

    private List<Coupon> coupons(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Coupon.builder().couponNumber("PU-M000000%d".formatted(i)).build())
                .toList();
    }

    private ErpSaleRequest.Line wholeBookLine(Long fuelTypeId, BigDecimal denomination, int quantity) {
        return new ErpSaleRequest.Line(fuelTypeId, denomination, quantity, null, true);
    }

    /** `count` coupons all in one book, keyed by (batch sequence number, book number). */
    private List<Coupon> bookCoupons(long batchSeq, int bookNumber, int count) {
        CouponBatch batch = CouponBatch.builder().sequenceNumber(batchSeq).build();
        List<Coupon> out = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            out.add(Coupon.builder()
                    .couponNumber("PU-%d-%d-%03d".formatted(batchSeq, bookNumber, i))
                    .bookNumber(bookNumber)
                    .batch(batch)
                    .build());
        }
        return out;
    }

    /** `bookCount` intact 100-coupon books in batch 1, books 1..bookCount, in selling order. */
    private List<Coupon> fullBooks(int bookCount) {
        List<Coupon> all = new ArrayList<>();
        for (int b = 1; b <= bookCount; b++) {
            all.addAll(bookCoupons(1L, b, 100));
        }
        return all;
    }

    /** save(): stamp an id on first persist, echo the same instance thereafter. */
    private void stubSaveAssigningId(long id) {
        when(couponSaleRepository.save(any(CouponSale.class))).thenAnswer(invocation -> {
            CouponSale sale = invocation.getArgument(0);
            if (sale.getId() == null) {
                sale.setId(id);
            }
            return sale;
        });
    }

    @Test
    void receiveSaleAssignsEveryLineAndPublishesOneEvent() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-001")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(fuelTypeRepository.findById(2L)).thenReturn(Optional.of(diesel));
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), eq(D20), eq("STOCKS"), anyInt()))
                .thenReturn(coupons(50));
        when(couponRepository.findIssuableForSale(eq(1L), eq(2L), eq(D50), eq("STOCKS"), anyInt()))
                .thenReturn(coupons(10));
        stubSaveAssigningId(42L);

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-001",
                line(1L, D20, 50), line(2L, D50, 10)));

        assertThat(response.status()).isEqualTo(SaleStatus.ASSIGNED);
        assertThat(response.lines()).hasSize(2);
        assertThat(response.lines()).allSatisfy(l -> assertThat(l.status()).isEqualTo(SaleLineStatus.ASSIGNED));
        assertThat(response.lines().get(0).lineNumber()).isEqualTo(1);
        assertThat(response.lines().get(0).couponNumbers()).hasSize(50);
        assertThat(response.lines().get(1).couponNumbers()).hasSize(10);
        verify(couponLifecycleService, times(2)).allocateForSale(anyList(), eq("ERP-SALE"), eq(42L));
        verify(eventPublisher, times(1)).publishEvent(any(SaleAssignedEvent.class));
    }

    @Test
    void receiveSalePartiallyAssignsWhenOneLineIsShort() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-002")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), eq(D20), eq("STOCKS"), anyInt()))
                .thenReturn(coupons(50));
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), eq(D50), eq("STOCKS"), anyInt()))
                .thenReturn(coupons(3));
        stubSaveAssigningId(42L);

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-002",
                line(1L, D20, 50), line(1L, D50, 10)));

        assertThat(response.status()).isEqualTo(SaleStatus.PARTIALLY_ASSIGNED);
        assertThat(response.lines().get(0).status()).isEqualTo(SaleLineStatus.ASSIGNED);
        assertThat(response.lines().get(1).status()).isEqualTo(SaleLineStatus.FAILED);
        assertThat(response.lines().get(1).failureReason()).contains("Only 3 of 10");
        assertThat(response.lines().get(1).couponNumbers()).isEmpty();
        verify(couponLifecycleService, times(1)).allocateForSale(anyList(), eq("ERP-SALE"), eq(42L));
        verify(eventPublisher, times(1)).publishEvent(any(SaleAssignedEvent.class));
    }

    @Test
    void receiveSaleFailsWhenNoLineCanBeFilledAndPublishesNoEvent() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-003")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), any(), eq("STOCKS"), anyInt()))
                .thenReturn(coupons(1));
        stubSaveAssigningId(42L);

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-003",
                line(1L, D20, 50), line(1L, D50, 10)));

        assertThat(response.status()).isEqualTo(SaleStatus.FAILED);
        assertThat(response.lines()).allSatisfy(l -> {
            assertThat(l.status()).isEqualTo(SaleLineStatus.FAILED);
            assertThat(l.couponNumbers()).isEmpty();
        });
        verify(couponLifecycleService, never()).allocateForSale(anyList(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(SaleAssignedEvent.class));
    }

    @Test
    void receiveSaleIsIdempotentOnDocumentNumber() {
        CouponSale existing = CouponSale.builder()
                .id(7L).bcDocumentNumber("SI-004").location(siteA)
                .status(SaleStatus.ASSIGNED).build();
        when(couponSaleRepository.findByBcDocumentNumber("SI-004")).thenReturn(Optional.of(existing));

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-004", line(1L, D20, 3)));

        assertThat(response.id()).isEqualTo(7L);
        verify(couponSaleRepository, never()).save(any());
        verify(couponLifecycleService, never()).allocateForSale(anyList(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(SaleAssignedEvent.class));
    }

    @Test
    void receiveSaleThrowsForUnknownLocationCode() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-005")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.receiveSale(saleRequest("SI-005", line(1L, D20, 1))))
                .isInstanceOf(LocationNotFoundException.class);
        verify(couponSaleRepository, never()).save(any());
    }

    @Test
    void receiveSaleThrowsWhenAnyLineHasAnUnknownFuelType() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-006")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(fuelTypeRepository.findById(2L)).thenReturn(Optional.empty());
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), eq(D20), eq("STOCKS"), anyInt()))
                .thenReturn(coupons(5));
        stubSaveAssigningId(42L);

        assertThatThrownBy(() -> service.receiveSale(saleRequest("SI-006",
                line(1L, D20, 5), line(2L, D50, 5))))
                .isInstanceOf(FuelTypeNotFoundException.class);
    }

    @Test
    void receiveSaleAssignsWholeBooksFromTheFrontOfTheQueue() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-010")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(couponRepository.lockIssuablePoolForSale(1L, 1L, D20, "STOCKS")).thenReturn(fullBooks(3));
        stubSaveAssigningId(50L);

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-010", wholeBookLine(1L, D20, 200)));

        assertThat(response.status()).isEqualTo(SaleStatus.ASSIGNED);
        assertThat(response.lines().get(0).status()).isEqualTo(SaleLineStatus.ASSIGNED);
        assertThat(response.lines().get(0).wholeBooks()).isTrue();
        assertThat(response.lines().get(0).couponNumbers())
                .hasSize(200)
                .startsWith("PU-1-1-001")
                .endsWith("PU-1-2-100")
                .doesNotContain("PU-1-3-001");
        verify(couponLifecycleService).allocateForSale(anyList(), eq("ERP-SALE"), eq(50L));
    }

    @Test
    void receiveSaleFailsWholeBookLineWhenNotEnoughIntactBooks() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-011")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(couponRepository.lockIssuablePoolForSale(1L, 1L, D20, "STOCKS")).thenReturn(fullBooks(1));
        stubSaveAssigningId(50L);

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-011", wholeBookLine(1L, D20, 200)));

        assertThat(response.status()).isEqualTo(SaleStatus.FAILED);
        assertThat(response.lines().get(0).failureReason()).contains("Only 1 of 2 requested intact book");
        assertThat(response.lines().get(0).couponNumbers()).isEmpty();
        verify(couponLifecycleService, never()).allocateForSale(anyList(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(SaleAssignedEvent.class));
    }

    @Test
    void receiveSaleWholeBookLineSkipsAPartiallyConsumedFrontBook() {
        List<Coupon> pool = new ArrayList<>();
        pool.addAll(bookCoupons(1L, 1, 99));   // front book already 1 short — not intact
        pool.addAll(bookCoupons(1L, 2, 100));
        pool.addAll(bookCoupons(1L, 3, 100));
        when(couponSaleRepository.findByBcDocumentNumber("SI-013")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(couponRepository.lockIssuablePoolForSale(1L, 1L, D20, "STOCKS")).thenReturn(pool);
        stubSaveAssigningId(50L);

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-013", wholeBookLine(1L, D20, 200)));

        assertThat(response.status()).isEqualTo(SaleStatus.ASSIGNED);
        assertThat(response.lines().get(0).couponNumbers())
                .hasSize(200)
                .doesNotContain("PU-1-1-001")
                .contains("PU-1-2-001", "PU-1-3-100");
    }

    @Test
    void receiveSaleRejectsWholeBookLineWhoseQuantityIsNotAMultipleOfBookSize() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-012")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        stubSaveAssigningId(50L);

        assertThatThrownBy(() -> service.receiveSale(saleRequest("SI-012", wholeBookLine(1L, D20, 150))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple of 100");
        verify(couponLifecycleService, never()).allocateForSale(anyList(), any(), any());
    }

    @Test
    void receiveSaleRetriesTheAssignmentOnTransientContentionThenSucceeds() {
        doThrow(new DeadlockLoserDataAccessException("deadlock", null))
                .doAnswer(CouponSaleServiceImplTest::runCallback)
                .when(assignmentTransaction).execute(any());
        when(couponSaleRepository.findByBcDocumentNumber("SI-020")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), eq(D20), eq("STOCKS"), anyInt()))
                .thenReturn(coupons(1));
        stubSaveAssigningId(42L);

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-020", line(1L, D20, 1)));

        assertThat(response.status()).isEqualTo(SaleStatus.ASSIGNED);
        verify(assignmentTransaction, times(2)).execute(any());
        verify(couponLifecycleService).allocateForSale(anyList(), eq("ERP-SALE"), eq(42L));
    }

    @Test
    void receiveSaleGivesUpAndRethrowsAfterMaxAttempts() {
        doThrow(new DeadlockLoserDataAccessException("deadlock", null))
                .when(assignmentTransaction).execute(any());

        assertThatThrownBy(() -> service.receiveSale(saleRequest("SI-021", line(1L, D20, 1))))
                .isInstanceOf(DeadlockLoserDataAccessException.class);

        verify(assignmentTransaction, times(3)).execute(any());
        verify(couponLifecycleService, never()).allocateForSale(anyList(), any(), any());
    }

    @Test
    void receiveSaleDoesNotRetryANonTransientFailure() {
        doThrow(new IllegalStateException("boom")).when(assignmentTransaction).execute(any());

        assertThatThrownBy(() -> service.receiveSale(saleRequest("SI-022", line(1L, D20, 1))))
                .isInstanceOf(IllegalStateException.class);

        verify(assignmentTransaction, times(1)).execute(any());
    }

    @Test
    void getByDocumentNumberThrowsWhenNotFound() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByDocumentNumber("SI-999"))
                .isInstanceOf(CouponSaleNotFoundException.class);
    }

    @Test
    void allocateForSaleReceivesTheFreshlyPersistedSaleIdAsReference() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-007")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), eq(D20), eq("STOCKS"), anyInt()))
                .thenReturn(coupons(1));
        stubSaveAssigningId(99L);

        service.receiveSale(saleRequest("SI-007", line(1L, D20, 1)));

        ArgumentCaptor<Long> referenceIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(couponLifecycleService).allocateForSale(anyList(), eq("ERP-SALE"), referenceIdCaptor.capture());
        assertThat(referenceIdCaptor.getValue()).isEqualTo(99L);
    }
}
