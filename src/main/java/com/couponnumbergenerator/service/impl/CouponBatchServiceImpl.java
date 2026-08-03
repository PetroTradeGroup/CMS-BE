package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.BatchFilterRequest;
import com.couponnumbergenerator.dto.response.CouponBatchResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.exception.CouponBatchNotFoundException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.repository.CouponBatchRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.projection.StatusCountRow;
import com.couponnumbergenerator.service.BulkConfigService;
import com.couponnumbergenerator.service.CouponBatchService;
import com.couponnumbergenerator.service.QrCodeService;
import com.couponnumbergenerator.specification.CouponBatchSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class CouponBatchServiceImpl implements CouponBatchService {

    private final CouponBatchRepository couponBatchRepository;
    private final CouponRepository couponRepository;
    private final BulkConfigService bulkConfigService;
    private final QrCodeService qrCodeService;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CouponBatchResponse> getBatches(BatchFilterRequest filter, Pageable pageable) {
        return PagedResponse.from(
                couponBatchRepository.findAll(CouponBatchSpecification.withFilters(filter), clampPageSize(pageable))
                        .map(CouponBatchResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponBatchResponse getBatch(Long id) {
        CouponBatch batch = couponBatchRepository.findById(id)
                .orElseThrow(() -> new CouponBatchNotFoundException(id));
        Map<CouponStatus, Long> statusCounts = new EnumMap<>(CouponStatus.class);
        for (StatusCountRow row : couponRepository.countByBatchGroupedByStatus(id)) {
            statusCounts.put(row.getStatus(), row.getCount());
        }
        return CouponBatchResponse.from(batch, statusCounts);
    }

    @Override
    @Transactional(readOnly = true)
    public void generateQrCodesZip(Long id, OutputStream outputStream) throws IOException {
        if (!couponBatchRepository.existsById(id)) {
            throw new CouponBatchNotFoundException(id);
        }
        List<Coupon> coupons = couponRepository.findByBatchIdOrderByBatchSequenceAsc(id);

        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (Coupon coupon : coupons) {
                String payload = qrCodeService.buildSignedPayload(coupon);
                byte[] png = qrCodeService.renderPng(payload);
                zip.putNextEntry(new ZipEntry(coupon.getCouponNumber() + ".png"));
                zip.write(png);
                zip.closeEntry();
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void generatePrintCsv(Long id, OutputStream outputStream) throws IOException {
        if (!couponBatchRepository.existsById(id)) {
            throw new CouponBatchNotFoundException(id);
        }
        List<Coupon> coupons = couponRepository.findByBatchIdOrderByBatchSequenceAsc(id);
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.write("coupon_number,fuel_type,denomination,expiry_date,batch_number,batch_sequence,qr_payload\r\n");
        for (Coupon coupon : coupons) {
            writer.write(String.join(",",
                    csvField(coupon.getCouponNumber()),
                    csvField(coupon.getFuelType().getName()),
                    csvField(coupon.getDenomination().toPlainString()),
                    csvField(coupon.getExpiryDate() == null ? "" : coupon.getExpiryDate().toString()),
                    csvField(coupon.getBatch() == null ? "" : coupon.getBatch().getBatchNumber()),
                    csvField(coupon.getBatchSequence() == null ? "" : coupon.getBatchSequence().toString()),
                    csvField(qrCodeService.buildSignedPayload(coupon))));
            writer.write("\r\n");
        }
        writer.flush();
    }

    private String csvField(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Pageable clampPageSize(Pageable pageable) {
        int max = bulkConfigService.getMaxPageSize();
        if (pageable.getPageSize() > max) {
            return PageRequest.of(pageable.getPageNumber(), max, pageable.getSort());
        }
        return pageable;
    }
}