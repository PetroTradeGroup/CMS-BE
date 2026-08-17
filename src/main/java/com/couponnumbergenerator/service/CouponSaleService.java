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
     * resolves the location and fuel type, draws {@code quantity} IN_STOCK coupons at that
     * location oldest-first (FIFO) and flips them to ALLOCATED, or — if there isn't enough
     * eligible stock — records the sale as FAILED rather than partially fulfilling it.
     * Publishes a sale-assigned event on success so the assigned range gets pushed back to BC.
     */
    CouponSaleResponse receiveSale(ErpSaleRequest request);

    CouponSaleResponse getByDocumentNumber(String bcDocumentNumber);

    /** The sales log, optionally filtered by status (defaults to all). */
    PagedResponse<CouponSaleResponse> getSales(SaleStatus status, Pageable pageable);
}