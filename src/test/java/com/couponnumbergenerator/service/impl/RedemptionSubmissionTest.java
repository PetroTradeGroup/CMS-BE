package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.constants.RedemptionCodes;
import com.couponnumbergenerator.dto.request.RedemptionSubmitRequest;
import com.couponnumbergenerator.dto.response.ScanResponse;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.exception.CouponAlreadyPendingRedemptionException;
import com.couponnumbergenerator.exception.CouponNotFoundException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import com.couponnumbergenerator.model.Department;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.CouponApprovalRequestRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.security.LocationAccessGuard;
import com.couponnumbergenerator.service.BulkConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Virtual-coupon redemption: redemption codes, no typed coupon numbers, single use once submitted. */
@ExtendWith(MockitoExtension.class)
class RedemptionSubmissionTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponApprovalRequestRepository couponApprovalRequestRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private LocationAccessGuard locationAccessGuard;
    @Mock private BulkConfigService bulkConfigService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private CouponLifecycleServiceImpl service;

    private final Coupon virtual = Coupon.builder().couponNumber("PUDSLM0000001").couponType(CouponType.DIGITAL)
            .redemptionCode("K7M4QX2").status(CouponStatus.ALLOCATED).denomination(new BigDecimal("20"))
            .fuelType(FuelType.builder().id(1L).name("DIESEL").build())
            .currentLocation(Location.builder().id(1L).code("HQ").build())
            .currentDepartment(Department.builder().id(1L).code("STOCKS").build())
            .build();

    @BeforeEach
    void setUp() {
        lenient().when(bulkConfigService.getMaxCount()).thenReturn(1000);
        lenient().when(locationAccessGuard.callerLocationCode(any())).thenReturn("STN-04");
        lenient().when(locationAccessGuard.callerUsername(any())).thenReturn("attendant1");
        lenient().when(locationRepository.findByCode("STN-04")).thenReturn(Optional.of(Location.builder().id(4L).code("STN-04").build()));
        lenient().when(couponRepository.lockByCouponNumberIn(List.of("PUDSLM0000001"))).thenReturn(List.of(virtual));
        lenient().when(couponApprovalRequestRepository.save(any())).thenAnswer(inv -> {
            CouponApprovalRequest approval = inv.getArgument(0);
            approval.setId(1L);
            return approval;
        });
    }

    private static RedemptionSubmitRequest request(List<String> numbers, List<String> codes) {
        return new RedemptionSubmitRequest(null, numbers, codes, "ABC1234", null);
    }

    @Test
    void anAccountWithNoStationCannotRedeem() {
        when(locationAccessGuard.callerLocationCode(any())).thenReturn(null);

        assertThatThrownBy(() -> service.submitRedemption(request(null, List.of("K7M4QX2"))))
                .isInstanceOf(AccessDeniedException.class);
        verify(couponApprovalRequestRepository, never()).save(any());
    }

    @Test
    void anExpiredCouponIsRefusedAtSubmitAndInThePreview() {
        virtual.setExpiryDate(LocalDate.now().minusDays(1));
        when(couponRepository.findByRedemptionCodeIn(anyCollection())).thenReturn(List.of(virtual));

        assertThatThrownBy(() -> service.submitRedemption(request(null, List.of("K7M4QX2"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired on");

        when(couponRepository.findByRedemptionCode("K7M4QX2")).thenReturn(Optional.of(virtual));
        ScanResponse preview = service.previewRedemptionByCode("k7m-4qx2");
        assertThat(preview.redeemable()).isFalse();
        assertThat(preview.message()).contains("expired on");
    }

    @Test
    void previewWarnsWhenTheCouponIsAlreadyPendingElsewhere() {
        when(couponRepository.findByRedemptionCode("K7M4QX2")).thenReturn(Optional.of(virtual));
        when(couponApprovalRequestRepository.findCouponNumbersInRequests(
                eq(ApprovalRequestType.REDEMPTION), eq(ApprovalStatus.PENDING), anyCollection()))
                .thenReturn(List.of("PUDSLM0000001"));

        ScanResponse preview = service.previewRedemptionByCode("K7M4QX2");

        assertThat(preview.redeemable()).isFalse();
        assertThat(preview.message()).contains("do not dispense");
    }

    @Test
    void previewOfAValidCodeIsRedeemable() {
        when(couponRepository.findByRedemptionCode("K7M4QX2")).thenReturn(Optional.of(virtual));

        ScanResponse preview = service.previewRedemptionByCode("K7M4QX2");

        assertThat(preview.redeemable()).isTrue();
        assertThat(preview.coupon().couponNumber()).isEqualTo("PUDSLM0000001");
    }

    @Test
    void previewOfAnUnknownCodeIs404() {
        when(couponRepository.findByRedemptionCode("ZZZZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewRedemptionByCode("ZZZZZZZ"))
                .isInstanceOf(CouponNotFoundException.class);
    }

    @Test
    void redeemsAVirtualCouponByItsCodeIgnoringCaseAndHyphens() {
        when(couponRepository.findByRedemptionCodeIn(Set.of("K7M4QX2"))).thenReturn(List.of(virtual));

        var response = service.submitRedemption(request(null, List.of("k7m-4qx2")));

        assertThat(response.status()).isEqualTo(ApprovalStatus.PENDING);
        verify(couponApprovalRequestRepository).save(any());
    }

    @Test
    void unknownCodeFailsTheSubmission() {
        when(couponRepository.findByRedemptionCodeIn(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitRedemption(request(null, List.of("ZZZZZZZ"))))
                .isInstanceOf(CouponNotFoundException.class)
                .hasMessageContaining("ZZZZZZZ");
    }

    @Test
    void typingAVirtualCouponsNumberIsRefused() {
        assertThatThrownBy(() -> service.submitRedemption(request(List.of("PUDSLM0000001"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redemption code");
        verify(couponApprovalRequestRepository, never()).save(any());
    }

    @Test
    void aCouponAlreadyPendingRedemptionElsewhereIsRefused() {
        when(couponRepository.findByRedemptionCodeIn(anyCollection())).thenReturn(List.of(virtual));
        when(couponApprovalRequestRepository.findCouponNumbersInRequests(
                eq(ApprovalRequestType.REDEMPTION), eq(ApprovalStatus.PENDING), anyCollection()))
                .thenReturn(List.of("PUDSLM0000001"));

        assertThatThrownBy(() -> service.submitRedemption(request(null, List.of("K7M4QX2"))))
                .isInstanceOf(CouponAlreadyPendingRedemptionException.class);
        verify(couponApprovalRequestRepository, never()).save(any());
    }

    @Test
    void generatedCodesUseTheUnambiguousAlphabet() {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 1000; i++) {
            assertThat(RedemptionCodes.generate(random)).matches("[A-HJKMNP-Z2-9]{7}");
        }
    }
}
