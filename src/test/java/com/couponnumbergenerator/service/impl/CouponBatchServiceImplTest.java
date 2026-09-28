package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.exception.CouponBatchNotFoundException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.repository.CouponBatchRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.service.QrCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponBatchServiceImplTest {

    @Mock private CouponBatchRepository couponBatchRepository;
    @Mock private CouponRepository couponRepository;
    @Mock private com.couponnumbergenerator.service.BulkConfigService bulkConfigService;
    @Mock private QrCodeService qrCodeService;

    @InjectMocks
    private CouponBatchServiceImpl service;

    @Test
    void generatePrintCsvWritesHeaderAndOneRowPerCoupon() throws IOException {
        when(couponBatchRepository.existsById(1L)).thenReturn(true);
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(1L))
                .thenReturn(List.of(coupon("PTC-000001", 1), coupon("PTC-000002", 2)));
        when(qrCodeService.buildSignedPayload(any()))
                .thenAnswer(inv -> ((Coupon) inv.getArgument(0)).getCouponNumber() + "|signed");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.generatePrintCsv(1L, out);

        String[] lines = out.toString(StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines).containsExactly(
                "coupon_number,fuel_type,denomination,expiry_date,batch_number,batch_sequence_number,batch_sequence,book_number,qr_payload",
                "PTC-000001,Diesel,50.00,2026-12-31,BATCH-001,7,1,1,PTC-000001|signed",
                "PTC-000002,Diesel,50.00,2026-12-31,BATCH-001,7,2,1,PTC-000002|signed");
    }

    @Test
    void generatePrintCsvQuotesFieldsContainingCommas() throws IOException {
        Coupon coupon = coupon("PTC-000001", 1);
        coupon.getFuelType().setName("Diesel, Low Sulphur");
        when(couponBatchRepository.existsById(1L)).thenReturn(true);
        when(couponRepository.findByBatchIdOrderByBatchSequenceAsc(1L)).thenReturn(List.of(coupon));
        when(qrCodeService.buildSignedPayload(any())).thenReturn("payload|signed");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.generatePrintCsv(1L, out);

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("PTC-000001,\"Diesel, Low Sulphur\",50.00");
    }

    @Test
    void generatePrintCsvThrowsForUnknownBatch() {
        when(couponBatchRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.generatePrintCsv(99L, new ByteArrayOutputStream()))
                .isInstanceOf(CouponBatchNotFoundException.class);
    }

    private Coupon coupon(String number, int sequence) {
        return Coupon.builder()
                .couponNumber(number)
                .fuelType(FuelType.builder().name("Diesel").build())
                .denomination(new BigDecimal("50.00"))
                .expiryDate(LocalDate.of(2026, 12, 31))
                .batch(CouponBatch.builder().batchNumber("BATCH-001").sequenceNumber(7L).build())
                .batchSequence(sequence)
                .bookNumber(1)
                .build();
    }
}