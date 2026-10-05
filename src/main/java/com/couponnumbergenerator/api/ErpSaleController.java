package com.couponnumbergenerator.api;

import com.couponnumbergenerator.config.OpenApiConfig;
import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.ErpSaleRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.CouponSaleResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.SaleStatus;
import com.couponnumbergenerator.service.CouponSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    @PreAuthorize("hasRole('ERP_INTEGRATION')")
    @SecurityRequirement(name = OpenApiConfig.CLIENT_CREDENTIALS_SCHEME)
    @Operation(summary = "Receive a sale event from Business Central",
            description = "One event per BC sales document, carrying one or more lines (fuel type + "
                    + "denomination + amount, given as either a loose 'quantity' of coupons or a number of "
                    + "'books' of 100 — 'books' may be fractional, e.g. 1.5 books = 150 coupons, as long as "
                    + "it lands on a whole coupon count). Idempotent on documentNumber — redelivering the same "
                    + "document is a safe no-op that returns its existing state. Otherwise each line assigns "
                    + "its quantity of IN_STOCK coupons at locationCode in selling order and flips them to "
                    + "ALLOCATED; a line without enough eligible stock is recorded FAILED. A line flagged "
                    + "wholeBooks is filled with whole intact books (100 serials, all IN_STOCK) from the "
                    + "front of the queue, skipping any partly-used book — its quantity must be a multiple "
                    + "of 100 (else 4xx). The sale's status is the rollup: ASSIGNED (all lines), "
                    + "PARTIALLY_ASSIGNED (some), FAILED (none) — still 200, since a shortfall is a stock "
                    + "problem, not a delivery failure BC should retry forever. Always 200: check the "
                    + "returned status field, not the HTTP status. An unknown locationCode or fuelTypeId is "
                    + "a config error and fails the request (4xx).")
    public ResponseEntity<ApiResponse<CouponSaleResponse>> receiveSale(
            @Valid @RequestBody ErpSaleRequest request) {
        CouponSaleResponse response = couponSaleService.receiveSale(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Sale %s %s".formatted(response.bcDocumentNumber(), response.status()), response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','FINANCE')")
    @Operation(summary = "The sales log, optionally filtered by status (defaults to all)")
    public ResponseEntity<ApiResponse<PagedResponse<CouponSaleResponse>>> getSales(
            @Parameter(description = "Filter by status: RECEIVED, ASSIGNED, PARTIALLY_ASSIGNED, PUSHED, FAILED")
            @RequestParam(required = false) SaleStatus status,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(couponSaleService.getSales(status, pageable)));
    }

    @GetMapping("/{documentNumber}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','FINANCE')")
    @Operation(summary = "Get a sale by its BC document number")
    public ResponseEntity<ApiResponse<CouponSaleResponse>> getSale(@PathVariable String documentNumber) {
        return ResponseEntity.ok(ApiResponse.success(couponSaleService.getByDocumentNumber(documentNumber)));
    }
}