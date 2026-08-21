package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.ErpSaleRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.CouponSaleResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.service.CouponSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.ERP_SALES_PATH)
@Tag(name = "ERP Sales", description = "Phase 4 ERP integration (Option A — coupon-blind ERP): Business Central "
        + "posts sales here; the CMS assigns serials from its own stock and confirms the range back to BC. "
        + "See docs/erp-sales-integration-design.md.")
public class ErpSaleController {

    private final CouponSaleService couponSaleService;

    @PostMapping
    @Operation(summary = "Receive a sale event from Business Central",
            description = "Idempotent on documentNumber — redelivering the same document is a safe no-op that "
                    + "returns its existing state. Otherwise assigns quantity IN_STOCK coupons of fuelTypeId + "
                    + "denomination at locationCode, oldest stock first, and flips them to ALLOCATED. If there "
                    + "isn't enough eligible stock the sale is recorded FAILED (still 200 — the event was "
                    + "received; the shortfall is a stock problem, not a delivery failure BC should retry "
                    + "forever). Always 200: check the returned status field, not the HTTP status, to see the "
                    + "outcome.")
    public ResponseEntity<ApiResponse<CouponSaleResponse>> receiveSale(
            @Valid @RequestBody ErpSaleRequest request) {
        CouponSaleResponse response = couponSaleService.receiveSale(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Sale %s %s".formatted(response.bcDocumentNumber(), response.status()), response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @Operation(summary = "The sales log, optionally filtered by status (defaults to all)")
    public ResponseEntity<ApiResponse<PagedResponse<CouponSaleResponse>>> getSales(
            @Parameter(description = "Filter by status: RECEIVED, ASSIGNED, PUSHED, FAILED")
            @RequestParam(required = false) SaleStatus status,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(couponSaleService.getSales(status, pageable)));
    }

    @GetMapping("/{documentNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @Operation(summary = "Get a sale by its BC document number")
    public ResponseEntity<ApiResponse<CouponSaleResponse>> getSale(@PathVariable String documentNumber) {
        return ResponseEntity.ok(ApiResponse.success(couponSaleService.getByDocumentNumber(documentNumber)));
    }
}