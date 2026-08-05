package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.exception.InvalidQrSignatureException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.qr.QrProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrCodeServiceImplTest {

    private final QrCodeServiceImpl qrCodeService = new QrCodeServiceImpl(new QrProperties("test-secret"));

    private Coupon coupon(String couponNumber) {
        return Coupon.builder()
                .couponNumber(couponNumber)
                .fuelType(FuelType.builder().name("Petrol").build())
                .denomination(BigDecimal.valueOf(20))
                .couponType(CouponType.PHYSICAL)
                .expiryDate(LocalDate.of(2027, 1, 1))
                .build();
    }

    @Test
    void decodeAndVerify_roundTripsAGenuinePayload() {
        String payload = qrCodeService.buildSignedPayload(coupon("PU002M0000001"));

        assertThat(qrCodeService.decodeAndVerify(payload)).isEqualTo("PU002M0000001");
    }

    @Test
    void decodeAndVerify_rejectsATamperedPayload() {
        String payload = qrCodeService.buildSignedPayload(coupon("PU002M0000001"));
        String tampered = payload.replaceFirst("PU002M0000001", "PU002M0000002");

        assertThatThrownBy(() -> qrCodeService.decodeAndVerify(tampered))
                .isInstanceOf(InvalidQrSignatureException.class);
    }

    @Test
    void decodeAndVerify_rejectsAMalformedPayload() {
        assertThatThrownBy(() -> qrCodeService.decodeAndVerify("not-a-signed-payload"))
                .isInstanceOf(InvalidQrSignatureException.class);
    }
}
