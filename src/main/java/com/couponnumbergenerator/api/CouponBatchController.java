package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.BatchFilterRequest;
import com.couponnumbergenerator.dto.request.ReceiveBatchRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.CouponBatchResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.TransitionResultResponse;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.service.BatchExportService;
import com.couponnumbergenerator.service.CouponBatchService;
import com.couponnumbergenerator.service.CouponLifecycleService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.BATCHES_PATH)
@Tag(name = "Coupon Batches", description = "Generation batches and stock receipt")
public class CouponBatchController {

    private final CouponBatchService couponBatchService;
    private final CouponLifecycleService couponLifecycleService;
    private final BatchExportService batchExportService;

    @GetMapping
    @Operation(summary = "Search coupon batches with optional filters")
    public ResponseEntity<ApiResponse<PagedResponse<CouponBatchResponse>>> getBatches(
            @Parameter(description = "Filter by fuel type ID")
            @RequestParam(required = false) Long fuelTypeId,
            @Parameter(description = "Filter by coupon type: PHYSICAL or DIGITAL")
            @RequestParam(required = false) CouponType couponType,
            @Parameter(description = "Filter by origin location ID")
            @RequestParam(required = false) Long locationId,
            @Parameter(description = "Filter by creation date (from), format: yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Filter by creation date (to), format: yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Only batches with at least one coupon currently IN_STOCK — e.g. to pick a batch when fulfilling a requisition")
            @RequestParam(required = false) Boolean hasStock,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        BatchFilterRequest filter = new BatchFilterRequest(fuelTypeId, couponType, locationId, dateFrom, dateTo, hasStock);
        return ResponseEntity.ok(ApiResponse.success(couponBatchService.getBatches(filter, pageable)));
    }

    @GetMapping("/export/excel")
    @Operation(summary = "Export the batch list as an Excel workbook",
            description = "Same filters as GET /batches; one row per batch, newest first, no pagination.")
    public ResponseEntity<StreamingResponseBody> exportBatchesExcel(
            @RequestParam(required = false) Long fuelTypeId,
            @RequestParam(required = false) CouponType couponType,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean hasStock) {
        BatchFilterRequest filter = new BatchFilterRequest(fuelTypeId, couponType, locationId, dateFrom, dateTo, hasStock);
        StreamingResponseBody body = out -> batchExportService.exportExcel(filter, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"batches-%s.xlsx\"".formatted(exportTimestamp()))
                .contentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Export the batch list as a PDF",
            description = "Same filters as GET /batches; one row per batch, newest first, no pagination.")
    public ResponseEntity<StreamingResponseBody> exportBatchesPdf(
            @RequestParam(required = false) Long fuelTypeId,
            @RequestParam(required = false) CouponType couponType,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Boolean hasStock) {
        BatchFilterRequest filter = new BatchFilterRequest(fuelTypeId, couponType, locationId, dateFrom, dateTo, hasStock);
        StreamingResponseBody body = out -> batchExportService.exportPdf(filter, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"batches-%s.pdf\"".formatted(exportTimestamp()))
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    private String exportTimestamp() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get batch by ID, including per-status coupon counts")
    public ResponseEntity<ApiResponse<CouponBatchResponse>> getBatch(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(couponBatchService.getBatch(id)));
    }

    @GetMapping("/{id}/qrcodes")
    @Operation(summary = "Download a ZIP of QR code images for every coupon in a batch",
            description = "One signed, offline-verifiable PNG per coupon, named \"<couponNumber>.png\" — "
                    + "hand the ZIP to a print vendor as-is, no rendering or signing required on their end.")
    public ResponseEntity<StreamingResponseBody> getBatchQrCodes(@PathVariable Long id) {
        CouponBatchResponse batch = couponBatchService.getBatch(id);
        StreamingResponseBody body = out -> couponBatchService.generateQrCodesZip(id, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"%s-qrcodes.zip\"".formatted(batch.batchNumber()))
                .contentType(MediaType.valueOf("application/zip"))
                .body(body);
    }

    @GetMapping("/{id}/print-csv")
    @Operation(summary = "Download a variable-data-printing CSV for every coupon in a batch",
            description = "One row per coupon: coupon number, fuel type, denomination, expiry, batch number, "
                    + "sequence, and the signed QR payload as text — the print vendor's VDP software merges each "
                    + "row into the coupon artwork and renders the QR code from the qr_payload column, so no "
                    + "image matching is needed.")
    public ResponseEntity<StreamingResponseBody> getBatchPrintCsv(@PathVariable Long id) {
        CouponBatchResponse batch = couponBatchService.getBatch(id);
        StreamingResponseBody body = out -> couponBatchService.generatePrintCsv(id, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"%s-print.csv\"".formatted(batch.batchNumber()))
                .contentType(MediaType.valueOf("text/csv"))
                .body(body);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
    @Operation(summary = "Receive a batch into stock",
            description = "Moves all GENERATED coupons of the batch to IN_STOCK at the given location (defaults to the batch's origin location).")
    public ResponseEntity<ApiResponse<TransitionResultResponse>> receiveBatch(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReceiveBatchRequest request) {
        TransitionResultResponse result = couponLifecycleService.receiveBatch(
                id, request == null ? ReceiveBatchRequest.empty() : request);
        return ResponseEntity.ok(
                ApiResponse.success("%d coupon(s) received into stock".formatted(result.count()), result));
    }
}