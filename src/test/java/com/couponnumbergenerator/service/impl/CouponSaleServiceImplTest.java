package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.ErpSaleRequest;
import com.couponnumbergenerator.dto.response.CouponSaleResponse;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.event.SaleAssignedEvent;
import com.couponnumbergenerator.exception.CouponSaleNotFoundException;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.Coupon;
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
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @InjectMocks
    private CouponSaleServiceImpl service;

    private Location siteA;
    private FuelType petrol;

    @BeforeEach
    void setUp() {
        siteA = Location.builder().id(1L).code("SITE-A").name("Site A").build();
        petrol = FuelType.builder().id(1L).name("Petrol").typeCode("PU").active(true).build();
    }

    private ErpSaleRequest saleRequest(String docNumber, int quantity) {
        return new ErpSaleRequest(docNumber, "SITE-A", 1L, new BigDecimal("20.00"), quantity, "cust-ref");
    }

    private List<Coupon> coupons(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> Coupon.builder().couponNumber("PU-M000000%d".formatted(i)).build())
                .toList();
    }

    @Test
    void receiveSaleAssignsIssuableCouponsAndPublishesEvent() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-001")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        List<Coupon> issuable = coupons(3);
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), eq(new BigDecimal("20.00")), eq("STOCKS"), any(PageRequest.class)))
                .thenReturn(issuable);
        when(couponSaleRepository.save(any(CouponSale.class))).thenAnswer(invocation -> {
            CouponSale sale = invocation.getArgument(0);
            sale.setId(42L);
            return sale;
        });

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-001", 3));

        assertThat(response.status()).isEqualTo(SaleStatus.ASSIGNED);
        assertThat(response.couponNumbers()).hasSize(3);
        assertThat(response.failureReason()).isNull();
        verify(couponLifecycleService).allocateForSale(eq(issuable), eq("ERP-SALE"), eq(42L));
        verify(eventPublisher).publishEvent(any(SaleAssignedEvent.class));
    }

    @Test
    void receiveSaleFailsWhenNotEnoughStockAndDoesNotAllocateOrPublish() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-002")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), eq(new BigDecimal("20.00")), eq("STOCKS"), any(PageRequest.class)))
                .thenReturn(coupons(2));
        when(couponSaleRepository.save(any(CouponSale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-002", 5));

        assertThat(response.status()).isEqualTo(SaleStatus.FAILED);
        assertThat(response.failureReason()).contains("Only 2 of 5");
        assertThat(response.couponNumbers()).isEmpty();
        verify(couponLifecycleService, never()).allocateForSale(anyList(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(SaleAssignedEvent.class));
    }

    @Test
    void receiveSaleIsIdempotentOnDocumentNumber() {
        CouponSale existing = CouponSale.builder()
                .id(7L).bcDocumentNumber("SI-003").location(siteA).fuelType(petrol)
                .denomination(new BigDecimal("20.00")).requestedCount(3).status(SaleStatus.ASSIGNED)
                .couponNumbers(List.of("PU-M0000001")).build();
        when(couponSaleRepository.findByBcDocumentNumber("SI-003")).thenReturn(Optional.of(existing));

        CouponSaleResponse response = service.receiveSale(saleRequest("SI-003", 3));

        assertThat(response.id()).isEqualTo(7L);
        verify(couponSaleRepository, never()).save(any());
        verify(couponLifecycleService, never()).allocateForSale(anyList(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(SaleAssignedEvent.class));
    }

    @Test
    void receiveSaleThrowsForUnknownLocationCode() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-004")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.receiveSale(saleRequest("SI-004", 1)))
                .isInstanceOf(LocationNotFoundException.class);
        verify(couponSaleRepository, never()).save(any());
    }

    @Test
    void receiveSaleThrowsForUnknownFuelType() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-005")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.receiveSale(saleRequest("SI-005", 1)))
                .isInstanceOf(FuelTypeNotFoundException.class);
    }

    @Test
    void getByDocumentNumberThrowsWhenNotFound() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByDocumentNumber("SI-999"))
                .isInstanceOf(CouponSaleNotFoundException.class);
    }

    @Test
    void allocateForSaleReceivesTheFreshlyPersistedSaleIdAsReference() {
        when(couponSaleRepository.findByBcDocumentNumber("SI-006")).thenReturn(Optional.empty());
        when(locationRepository.findByCode("SITE-A")).thenReturn(Optional.of(siteA));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(petrol));
        when(couponRepository.findIssuableForSale(eq(1L), eq(1L), eq(new BigDecimal("20.00")), eq("STOCKS"), any(PageRequest.class)))
                .thenReturn(coupons(1));
        when(couponSaleRepository.save(any(CouponSale.class))).thenAnswer(invocation -> {
            CouponSale sale = invocation.getArgument(0);
            sale.setId(99L);
            return sale;
        });

        service.receiveSale(saleRequest("SI-006", 1));

        ArgumentCaptor<Long> referenceIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(couponLifecycleService).allocateForSale(anyList(), eq("ERP-SALE"), referenceIdCaptor.capture());
        assertThat(referenceIdCaptor.getValue()).isEqualTo(99L);
    }
}