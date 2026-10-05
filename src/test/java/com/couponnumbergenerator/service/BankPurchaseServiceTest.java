package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.BankAmountPurchaseRequest;
import com.couponnumbergenerator.dto.request.BankLitresPurchaseRequest;
import com.couponnumbergenerator.dto.request.BankPurchaseRequest;
import com.couponnumbergenerator.dto.request.DenominationLine;
import com.couponnumbergenerator.dto.request.GenerateBulkCouponRequest;
import com.couponnumbergenerator.dto.response.BankPurchaseResponse;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.enums.BankPurchaseStatus;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.exception.BankPurchaseNotFoundException;
import com.couponnumbergenerator.exception.PurchaseByAmountException;
import com.couponnumbergenerator.model.BankPurchase;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.repository.BankPurchaseRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankPurchaseServiceTest {

    @Mock private BankPurchaseRepository bankPurchaseRepository;
    @Mock private FuelTypeRepository fuelTypeRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private CouponService couponService;
    @Mock private CouponLifecycleService couponLifecycleService;
    @Mock private QrCodeService qrCodeService;
    @InjectMocks private BankPurchaseService service;

    private final FuelType diesel = FuelType.builder().id(1L).name("DIESEL").pricePerLitre(new BigDecimal("1.55")).build();
    private final CouponBatch batch = CouponBatch.builder().id(9L).build();

    /** 2 × 20 L + 1 × 10 L = 50 L at 1.55 = 77.50 */
    private BankPurchaseRequest request(String amount) {
        return new BankPurchaseRequest("TXN-1", "cust-7", 1L,
                List.of(new DenominationLine(new BigDecimal("20"), 2), new DenominationLine(new BigDecimal("10"), 1)),
                new BigDecimal(amount));
    }

    @Test
    void mintsDigitalCouponsAndAllocatesThemToThePurchase() {
        Coupon coupon = Coupon.builder().couponNumber("PU1").batch(batch).status(CouponStatus.ALLOCATED).build();
        CouponResponse generated = mock(CouponResponse.class);
        when(generated.couponNumber()).thenReturn("PU1");
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-1")).thenReturn(Optional.empty());
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(diesel));
        when(couponService.generateBulkCoupons(any())).thenReturn(List.of(generated));
        when(couponRepository.findByCouponNumberIn(List.of("PU1"))).thenReturn(List.of(coupon));
        when(bankPurchaseRepository.save(any())).thenAnswer(inv -> {
            BankPurchase p = inv.getArgument(0);
            p.setId(5L);
            return p;
        });
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(9L)).thenReturn(List.of(coupon));
        when(qrCodeService.buildSignedPayload(coupon)).thenReturn("PU1|sig");

        BankPurchaseResponse response = service.purchase("bank-a", request("77.5"));

        ArgumentCaptor<GenerateBulkCouponRequest> gen = ArgumentCaptor.forClass(GenerateBulkCouponRequest.class);
        verify(couponService).generateBulkCoupons(gen.capture());
        assertThat(gen.getValue().couponType()).isEqualTo(CouponType.DIGITAL);
        verify(couponLifecycleService).transitionForBankPurchase(eq(List.of(coupon)), eq(CouponStatus.ALLOCATED),
                anyString(), eq(BankPurchaseService.BANK_ACTOR), eq(5L));
        assertThat(response.bankCode()).isEqualTo("bank-a");
        assertThat(response.amount()).isEqualByComparingTo("77.50");
        assertThat(response.litres()).isEqualByComparingTo("50");
        assertThat(response.coupons()).singleElement().extracting(BankPurchaseResponse.VirtualCoupon::qrPayload)
                .isEqualTo("PU1|sig");
    }

    @Test
    void rejectsAnAmountThatDoesNotMatchTheCurrentPrice() {
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-1")).thenReturn(Optional.empty());
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(diesel));

        assertThatThrownBy(() -> service.purchase("bank-a", request("70.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("77.50");
        verifyNoInteractions(couponService);
    }

    @Test
    void rejectsAFuelTypeWithNoPrice() {
        diesel.setPricePerLitre(null);
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-1")).thenReturn(Optional.empty());
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(diesel));

        assertThatThrownBy(() -> service.purchase("bank-a", request("77.50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not on sale");
    }

    @Test
    void repostingTheSameReferenceReturnsTheOriginalWithoutMintingAgain() {
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-1")).thenReturn(Optional.of(issued()));

        service.purchase("bank-a", request("77.50"));

        verifyNoInteractions(couponService);
    }

    @Test
    void reusingAReferenceForADifferentAmountIsRejected() {
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-1")).thenReturn(Optional.of(issued()));

        assertThatThrownBy(() -> service.purchase("bank-a", request("99.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different purchase");
    }

    @Test
    void reversalCancelsTheCouponsAndIsIdempotent() {
        BankPurchase purchase = issued();
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-1")).thenReturn(Optional.of(purchase));
        when(bankPurchaseRepository.save(purchase)).thenReturn(purchase);

        service.reverse("bank-a", "TXN-1", "refund");
        service.reverse("bank-a", "TXN-1", "refund");

        assertThat(purchase.getStatus()).isEqualTo(BankPurchaseStatus.REVERSED);
        verify(couponLifecycleService).transitionForBankPurchase(anyList(), eq(CouponStatus.CANCELLED),
                eq("refund"), anyString(), anyLong());
        verify(bankPurchaseRepository, never()).delete(any());
    }

    @Test
    void anotherBanksPurchaseIsNotFound() {
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-b", "TXN-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("bank-b", "TXN-1"))
                .isInstanceOf(BankPurchaseNotFoundException.class);
        assertThatThrownBy(() -> service.reverse("bank-b", "TXN-1", "refund"))
                .isInstanceOf(BankPurchaseNotFoundException.class);
        verifyNoInteractions(couponLifecycleService);
    }

    @Test
    void quoteOffersTheWholeLitresUnderAndOverTheAmount() {
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(diesel));

        // 50.00 / 1.55 = 32.26 L → 32 L for 49.60 or 33 L for 51.15
        assertThat(service.quote(1L, new BigDecimal("50.00")).options())
                .extracting(o -> o.litres() + "L@" + o.amount().toPlainString())
                .containsExactly("32L@49.60", "33L@51.15");
        // Exactly 32 L worth → one option
        assertThat(service.quote(1L, new BigDecimal("49.60")).options())
                .extracting(o -> o.litres() + "L@" + o.amount().toPlainString())
                .containsExactly("32L@49.60");
        // Less than a litre's worth → only 1 L
        assertThat(service.quote(1L, new BigDecimal("1.00")).options())
                .extracting(o -> o.litres() + "L@" + o.amount().toPlainString())
                .containsExactly("1L@1.55");
    }

    @Test
    void amountPurchaseWorksOutTheLitresAsOneCoupon() {
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-2")).thenReturn(Optional.empty());
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(diesel));
        when(couponService.generateBulkCoupons(any())).thenReturn(List.of());
        when(couponRepository.findByCouponNumberIn(List.of())).thenReturn(List.of(
                Coupon.builder().couponNumber("PU1").batch(batch).build()));
        when(bankPurchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BankPurchaseResponse response = service.purchaseByAmount("bank-a",
                new BankAmountPurchaseRequest("TXN-2", null, 1L, new BigDecimal("51.15")));

        ArgumentCaptor<GenerateBulkCouponRequest> gen = ArgumentCaptor.forClass(GenerateBulkCouponRequest.class);
        verify(couponService).generateBulkCoupons(gen.capture());
        assertThat(gen.getValue().lines()).singleElement().satisfies(line -> {
            assertThat(line.denomination()).isEqualByComparingTo("33");
            assertThat(line.resolvedCount()).isEqualTo(1);
        });
        assertThat(response.litres()).isEqualByComparingTo("33");
    }

    @Test
    void anAmountThatIsNotWholeLitresIsRejectedWithTheNearestAmounts() {
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-2")).thenReturn(Optional.empty());
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(diesel));

        assertThatThrownBy(() -> service.purchaseByAmount("bank-a",
                new BankAmountPurchaseRequest("TXN-2", null, 1L, new BigDecimal("50.00"))))
                .isInstanceOf(PurchaseByAmountException.class)
                .hasMessageContaining("49.60 for 32 L or 51.15 for 33 L");
        verifyNoInteractions(couponService);
    }

    @Test
    void amountPurchaseRetryReturnsTheOriginalEvenAfterAPriceChange() {
        diesel.setPricePerLitre(new BigDecimal("1.60"));   // 77.50 no longer buys whole litres
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-1")).thenReturn(Optional.of(issued()));

        BankPurchaseResponse response = service.purchaseByAmount("bank-a",
                new BankAmountPurchaseRequest("TXN-1", null, 1L, new BigDecimal("77.50")));

        assertThat(response.bankReference()).isEqualTo("TXN-1");
        verifyNoInteractions(couponService);
    }

    @Test
    void litresPurchaseWorksOutTheAmount() {
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-4")).thenReturn(Optional.empty());
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(diesel));
        when(couponService.generateBulkCoupons(any())).thenReturn(List.of());
        when(couponRepository.findByCouponNumberIn(List.of())).thenReturn(List.of(
                Coupon.builder().couponNumber("PU1").batch(batch).build()));
        when(bankPurchaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BankPurchaseResponse response = service.purchaseByLitres("bank-a",
                new BankLitresPurchaseRequest("TXN-4", null, 1L, 33));

        assertThat(response.amount()).isEqualByComparingTo("51.15");
        assertThat(response.litres()).isEqualByComparingTo("33");
        ArgumentCaptor<GenerateBulkCouponRequest> gen = ArgumentCaptor.forClass(GenerateBulkCouponRequest.class);
        verify(couponService).generateBulkCoupons(gen.capture());
        assertThat(gen.getValue().lines()).singleElement()
                .satisfies(line -> assertThat(line.resolvedCount()).isEqualTo(1));
    }

    @Test
    void litresPurchaseRetryReturnsTheOriginalAmountAfterAPriceChange() {
        diesel.setPricePerLitre(new BigDecimal("1.60"));
        when(bankPurchaseRepository.findByBankCodeAndBankReference("bank-a", "TXN-1")).thenReturn(Optional.of(issued()));

        BankPurchaseResponse response = service.purchaseByLitres("bank-a",
                new BankLitresPurchaseRequest("TXN-1", null, 1L, 50));

        assertThat(response.amount()).isEqualByComparingTo("77.50");
        verifyNoInteractions(couponService);
        assertThatThrownBy(() -> service.purchaseByLitres("bank-a", new BankLitresPurchaseRequest("TXN-1", null, 1L, 40)))
                .hasMessageContaining("different purchase");
    }

    private BankPurchase issued() {
        return BankPurchase.builder().id(5L).bankCode("bank-a").bankReference("TXN-1").fuelType(diesel).batch(batch)
                .litres(new BigDecimal("50")).pricePerLitre(new BigDecimal("1.55"))
                .amount(new BigDecimal("77.50")).status(BankPurchaseStatus.ISSUED).build();
    }
}
