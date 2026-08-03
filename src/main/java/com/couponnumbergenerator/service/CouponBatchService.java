package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.BatchFilterRequest;
import com.couponnumbergenerator.dto.response.CouponBatchResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.io.OutputStream;

public interface CouponBatchService {

    PagedResponse<CouponBatchResponse> getBatches(BatchFilterRequest filter, Pageable pageable);

    /** Batch detail including the live per-status counts of its coupons. */
    CouponBatchResponse getBatch(Long id);

    /**
     * Streams a ZIP of one signed QR PNG per coupon in the batch, named "&lt;couponNumber&gt;.png",
     * for handoff to third-party printers — they place each image, no rendering/signing on their end.
     */
    void generateQrCodesZip(Long id, OutputStream outputStream) throws IOException;

    /**
     * Streams a CSV with one row per coupon in the batch (coupon number, fuel type, denomination,
     * expiry, batch number, sequence, and the signed QR payload as text) for variable-data printing —
     * the vendor's VDP software merges each row into the coupon artwork and renders the QR itself.
     */
    void generatePrintCsv(Long id, OutputStream outputStream) throws IOException;
}