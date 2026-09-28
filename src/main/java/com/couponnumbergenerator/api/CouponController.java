package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.CouponFilterRequest;
import com.couponnumbergenerator.dto.request.GenerateBulkCouponRequest;
import com.couponnumbergenerator.dto.request.GenerateCouponRequest;
import com.couponnumbergenerator.dto.request.ImportLegacyCouponRequest;
import com.couponnumbergenerator.dto.request.TransferRequest;
import com.couponnumbergenerator.dto.request.TransitionRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.BulkLegacyImportResponse;
import com.couponnumbergenerator.dto.response.CouponMovementResponse;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.TransferResultResponse;
import com.couponnumbergenerator.dto.response.TransitionResultResponse;
import com.couponnumbergenerator.enums.CouponOrigin;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.service.ActionOutcome;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.util.List;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.COUPONS_PATH)
@Tag(name = "Coupons", description = "Coupon number generation and management")
public class CouponController {

    private final CouponService couponService;
    private final CouponLifecycleService couponLifecycleService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
    @Operation(summary = "Generate a single coupon")
    public ResponseEntity<ApiResponse<CouponResponse>> generateCoupon(
            @Valid @RequestBody GenerateCouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Coupon generated successfully",
                        couponService.generateCoupon(request)));
    }

    @PostMapping("/generate/bulk")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
    @Operation(summary = "Generate bulk coupons", description = "Limit is managed via PATCH /api/v1/config/bulk-limit")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> generateBulkCoupons(
            @Valid @RequestBody GenerateBulkCouponRequest request) {
        List<CouponResponse> responses = couponService.generateBulkCoupons(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(request.totalCount() + " coupon(s) generated successfully", responses));
    }

    @PostMapping("/legacy-import")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
    @Operation(summary = "Register a pre-existing (e.g. old barcoded) coupon directly at ALLOCATED",
            description = "For a coupon that predates this system and was never generated here — registers it "
                    + "with the given couponNumber (e.g. the value encoded in its barcode) directly at ALLOCATED, "
                    + "skipping generation/receipt, so it can be redeemed through POST /redemptions like any "
                    + "other coupon. Fails with 400 if couponNumber is already in use.")
    public ResponseEntity<ApiResponse<CouponResponse>> importLegacyCoupon(
            @Valid @RequestBody ImportLegacyCouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Legacy coupon registered", couponService.importLegacyCoupon(request)));
    }

    @PostMapping(value = "/legacy-import/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
    @Operation(summary = "Register a spreadsheet of pre-existing coupons directly at ALLOCATED",
            description = "Bulk sibling of POST /coupons/legacy-import: upload an .xlsx with a header row and "
                    + "one legacy coupon per row — couponNumber, fuelTypeCode, denomination, locationCode "
                    + "(optional, defaults as for the single-row endpoint), departmentCode (optional), expiryDate "
                    + "(optional, yyyy-MM-dd). Every row is validated independently — a bad row (duplicate "
                    + "number, unknown code) is reported in the response, not thrown, so it never blocks the "
                    + "rest of the file. Pass dryRun=true to validate the whole file without registering "
                    + "anything — check it before it becomes real, redeemable stock.")
    public ResponseEntity<ApiResponse<BulkLegacyImportResponse>> importLegacyCoupons(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @RequestParam(required = false) String performedBy) {
        BulkLegacyImportResponse response = couponService.importLegacyCoupons(file, dryRun, performedBy);
        String message = dryRun
                ? "Dry run: %d of %d row(s) would be registered".formatted(response.succeeded(), response.totalRows())
                : "%d of %d row(s) registered".formatted(response.succeeded(), response.totalRows());
        return ResponseEntity.status(dryRun ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(message, response));
    }

    @PostMapping("/transitions")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
    @Operation(summary = "Transition coupons to a new lifecycle status",
            description = "All-or-nothing bulk transition validated against the lifecycle state machine. "
                    + "A reason is required for CANCELLED and FLAGGED. If toLocationId is set, the move is deferred "
                    + "for supervisor approval (202) instead of applying immediately (200). Returns 409 on an illegal transition.")
    public ResponseEntity<ApiResponse<?>> transition(
            @Valid @RequestBody TransitionRequest request) {
        ActionOutcome<TransitionResultResponse> outcome = couponLifecycleService.transition(request);
        return switch (outcome) {
            case ActionOutcome.Applied<TransitionResultResponse> applied -> ResponseEntity.ok(ApiResponse.success(
                    "%d coupon(s) transitioned to %s".formatted(applied.result().count(), applied.result().targetStatus()),
                    applied.result()));
            case ActionOutcome.Pending<TransitionResultResponse> pending -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(
                            "Submitted for supervisor approval (request #%d)".formatted(pending.request().id()),
                            pending.request()));
        };
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
    @Operation(summary = "Transfer a whole batch, a position range, or a denomination breakdown to a new location/department",
            description = "Selects coupons by batch instead of naming coupon numbers: the whole batch (default), "
                    + "a 1-indexed inclusive rangeStart/rangeEnd position range, or a denominationLines breakdown — "
                    + "for each line, the first `count` *eligible* coupons of that `denomination`, in batch order "
                    + "(e.g. 5 x 20L + 3 x 50L in one call); an ineligible coupon at a position is skipped in favor "
                    + "of the next eligible one — range and denomination selection are mutually exclusive. Moving "
                    + "toLocationId and/or toDepartmentId defers the transfer for supervisor approval (202) instead "
                    + "of applying immediately (200); targetStatus is optional — omit it for a pure reassignment "
                    + "that keeps the current status. A pure reassignment only ever touches coupons currently "
                    + "IN_STOCK — once a coupon has been allocated, redeemed, or is already mid-transfer "
                    + "(IN_TRANSIT), it can't be relocated again until it's received back into stock. For a range/"
                    + "whole-batch selection (positions taken as given) one ineligible coupon rejects the whole "
                    + "request (409, names the coupon and its current status) before anything is touched or any "
                    + "approval-queue entry is created; a denomination pick instead fails only if it can't find "
                    + "enough eligible coupons of that denomination. The response lists every coupon actually "
                    + "moved, with its batch position, for a full audit trail of the transfer.")
    public ResponseEntity<ApiResponse<?>> transfer(
            @Valid @RequestBody TransferRequest request) {
        ActionOutcome<TransferResultResponse> outcome = couponLifecycleService.transferByBatch(request);
        return switch (outcome) {
            case ActionOutcome.Applied<TransferResultResponse> applied -> ResponseEntity.ok(ApiResponse.success(
                    "%d coupon(s) transferred".formatted(applied.result().count()), applied.result()));
            case ActionOutcome.Pending<TransferResultResponse> pending -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(
                            "Submitted for supervisor approval (request #%d)".formatted(pending.request().id()),
                            pending.request()));
        };
    }

    @GetMapping("/{couponNumber}/history")
    @Operation(summary = "Get the movement history of a coupon, oldest first")
    public ResponseEntity<ApiResponse<List<CouponMovementResponse>>> getHistory(
            @PathVariable String couponNumber) {
        return ResponseEntity.ok(ApiResponse.success(couponLifecycleService.getHistory(couponNumber)));
    }

    @GetMapping
    @Operation(summary = "Search coupons with optional filters")
    public ResponseEntity<ApiResponse<PagedResponse<CouponResponse>>> getCoupons(
            @Parameter(description = "Filter by creation date (from), format: yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Filter by creation date (to), format: yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Filter by fuel type ID")
            @RequestParam(required = false) Long fuelTypeId,
            @Parameter(description = "Filter by coupon status: GENERATED, IN_STOCK, IN_TRANSIT, ALLOCATED, REDEEMED, EXPIRED, CANCELLED, FLAGGED")
            @RequestParam(required = false) CouponStatus status,
            @Parameter(description = "Filter by current location ID")
            @RequestParam(required = false) Long locationId,
            @Parameter(description = "Filter by coupon type: PHYSICAL or DIGITAL")
            @RequestParam(required = false) CouponType couponType,
            @Parameter(description = "Filter by batch ID")
            @RequestParam(required = false) Long batchId,
            @Parameter(description = "Filter by batch number (exact, case-insensitive), e.g. BAT-20260716110715490-002")
            @RequestParam(required = false) String batchNumber,
            @Parameter(description = "Filter by current department ID")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "Search by coupon number (partial, case-insensitive) — matches across the whole dataset, not just the current page")
            @RequestParam(required = false) String couponNumber,
            @Parameter(description = "Filter by origin: GENERATED or LEGACY_IMPORT — e.g. to see all legacy-imported coupons")
            @RequestParam(required = false) CouponOrigin origin,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        CouponFilterRequest filter = new CouponFilterRequest(dateFrom, dateTo, fuelTypeId, status,
                locationId, couponType, batchId, departmentId, batchNumber, couponNumber, origin);
        return ResponseEntity.ok(ApiResponse.success(couponService.getCoupons(filter, pageable)));
    }

    @GetMapping("/export")
    @Operation(summary = "Export all coupons as CSV", description = "Streams all matching records. Supports the same filters as GET /coupons. No pagination limit.")
    public ResponseEntity<StreamingResponseBody> exportCoupons(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long fuelTypeId,
            @RequestParam(required = false) CouponStatus status,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) CouponType couponType,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String batchNumber,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String couponNumber,
            @RequestParam(required = false) CouponOrigin origin) {
        CouponFilterRequest filter = new CouponFilterRequest(dateFrom, dateTo, fuelTypeId, status,
                locationId, couponType, batchId, departmentId, batchNumber, couponNumber, origin);
        StreamingResponseBody body = out -> couponService.exportCoupons(filter, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"coupons.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @GetMapping("/{couponNumber}")
    @Operation(summary = "Get coupon by number")
    public ResponseEntity<ApiResponse<CouponResponse>> getCouponByNumber(@PathVariable String couponNumber) {
        return ResponseEntity.ok(ApiResponse.success(couponService.getCouponByNumber(couponNumber)));
    }

    @GetMapping("/fuel-type/{fuelTypeId}")
    @Operation(summary = "Get coupons by fuel type ID")
    public ResponseEntity<ApiResponse<PagedResponse<CouponResponse>>> getCouponsByFuelType(
            @PathVariable Long fuelTypeId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(couponService.getCouponsByFuelType(fuelTypeId, pageable)));
    }
}
