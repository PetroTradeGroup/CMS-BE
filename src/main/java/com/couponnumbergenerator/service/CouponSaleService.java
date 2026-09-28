package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.ErpSaleRequest;
import com.couponnumbergenerator.dto.response.CouponSaleResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.SaleStatus;
import org.springframework.data.domain.Pageable;


public interface CouponSaleService {

    /**
     * Idempotent on {@link ErpSaleRequest#documentNumber()}: redelivering an already-received
     * document returns its existing state unchanged rather than assigning again. Otherwise,
     * resolves the location and, per line, draws {@code quantity} IN_STOCK coupons of that
     * line's fuel type + denomination in selling order and flips them to ALLOCATED. A line
     * without enough eligible stock is recorded FAILED rather than partially filled; the
     * sale's status is the rollup of its lines (ASSIGNED / PARTIALLY_ASSIGNED / FAILED).
     * Publishes a sale-assigned event when at least one line is assigned, so the assigned
     * ranges get pushed back to BC. An unknown location or fuel type is a config error, not a
     * stock shortfall — it fails the whole request (4xx) rather than being recorded.
     */
    CouponSaleResponse receiveSale(ErpSaleRequest request);

    CouponSaleResponse getByDocumentNumber(String bcDocumentNumber);

    /** The sales log, optionally filtered by status (defaults to all). */
    PagedResponse<CouponSaleResponse> getSales(SaleStatus status, Pageable pageable);
}