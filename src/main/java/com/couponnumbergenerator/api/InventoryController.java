package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.CouponFilterRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.BalanceTrendPoint;
import com.couponnumbergenerator.dto.response.CouponActivityResponse;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.dto.response.InventorySummaryResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.service.CouponService;
import com.couponnumbergenerator.service.InventoryService;
import com.couponnumbergenerator.service.RedemptionSummaryExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.INVENTORY_PATH)
@Tag(name = "Inventory", description = "Real-time stock visibility by location, fuel type and status")
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class InventoryController {

    private final InventoryService inventoryService;
    private final CouponService couponService;
    private final RedemptionSummaryExportService exportService;

    @GetMapping("/summary")
    @Operation(summary = "Stock-on-hand summary grouped by location, fuel type and status")
    public ResponseEntity<ApiResponse<List<InventorySummaryResponse>>> summarize(
            @Parameter(description = "Filter by location ID")
            @RequestParam(required = false) Long locationId,
            @Parameter(description = "Filter by fuel type ID")
            @RequestParam(required = false) Long fuelTypeId,
            @Parameter(description = "Filter by coupon status")
            @RequestParam(required = false) CouponStatus status) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.summarize(locationId, fuelTypeId, status)));
    }

    // Method-level @PreAuthorize replaces the class-level one, so STOCKS_CONTROLLER gets this report only.
    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','STOCKS_CONTROLLER')")
    @Operation(summary = "Volume that moved into each status over a day or a date range",
            description = "Coupon count and litres per status, then per fuel type with a denomination breakdown — "
                    + "e.g. status=REDEEMED&fuelTypeId=1&dateFrom=2026-09-01&dateTo=2026-09-30. Pass dateFrom/dateTo "
                    + "for a range, or date alone for one day; with none given, defaults to today. Counts status "
                    + "changes in the period (activity), not a balance: a coupon that enters a status twice counts twice.")
    public ResponseEntity<ApiResponse<CouponActivityResponse>> activity(
            @Parameter(description = "Single day (ISO, e.g. 2026-09-05) — shorthand for dateFrom=dateTo=date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Limit to one status; omit for every status")
            @RequestParam(required = false) CouponStatus status,
            @Parameter(description = "Limit to coupons that moved to this location")
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate[] range = RedemptionController.resolveRange(date, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.activity(range[0], range[1], status, locationId, fuelTypeId)));
    }

    @GetMapping("/activity/export/excel")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','STOCKS_CONTROLLER')")
    @Operation(summary = "Export the activity report as an Excel workbook", description = "Same filters as GET /activity.")
    public ResponseEntity<StreamingResponseBody> exportActivityExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) CouponStatus status,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate[] range = RedemptionController.resolveRange(date, dateFrom, dateTo);
        CouponActivityResponse activity = inventoryService.activity(range[0], range[1], status, locationId, fuelTypeId);
        StreamingResponseBody body = out -> exportService.exportActivityExcel(activity,
                "Coupon Activity: %s to %s".formatted(range[0], range[1]), out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"coupon-activity-%s-to-%s.xlsx\""
                        .formatted(range[0], range[1]))
                .contentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','STOCKS_CONTROLLER')")
    @Operation(summary = "Where coupons stood at the end of a date — stock balance and what's owed",
            description = "Litres and coupons per status as at the end of asAt (default today), then per fuel type "
                    + "and denomination. IN_STOCK is stock on hand; ALLOCATED is owed (allocated, not yet redeemed). "
                    + "Rebuilt from movement history, so any past date works.")
    public ResponseEntity<ApiResponse<CouponActivityResponse>> balance(
            @Parameter(description = "Balance at the end of this day (ISO); defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asAt,
            @RequestParam(required = false) CouponStatus status,
            @Parameter(description = "Limit to coupons that were at this location")
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long fuelTypeId) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.balance(asAt != null ? asAt : LocalDate.now(), status, locationId, fuelTypeId)));
    }

    @GetMapping("/balance/export/excel")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','STOCKS_CONTROLLER')")
    @Operation(summary = "Export the balance report as an Excel workbook", description = "Same params as GET /balance.")
    public ResponseEntity<StreamingResponseBody> exportBalanceExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asAt,
            @RequestParam(required = false) CouponStatus status,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate day = asAt != null ? asAt : LocalDate.now();
        CouponActivityResponse balance = inventoryService.balance(day, status, locationId, fuelTypeId);
        StreamingResponseBody body = out -> exportService.exportActivityExcel(balance,
                "Coupon Balance as at end of %s".formatted(day), out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"coupon-balance-%s.xlsx\"".formatted(day))
                .contentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/balance/trend")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','STOCKS_CONTROLLER')")
    @Operation(summary = "End-of-day litres per status for each day in a range — the dashboard line chart",
            description = "Defaults to the last 30 days. At most 366 days. One point per day: { date, litres: { IN_STOCK, ALLOCATED, ... } }.")
    public ResponseEntity<ApiResponse<List<BalanceTrendPoint>>> balanceTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(29);
        return ResponseEntity.ok(ApiResponse.success(inventoryService.balanceTrend(from, to, fuelTypeId)));
    }

    @GetMapping("/locations/{locationId}/coupons")
    @Operation(summary = "List the coupons currently held at a location")
    public ResponseEntity<ApiResponse<PagedResponse<CouponResponse>>> getLocationCoupons(
            @PathVariable Long locationId,
            @Parameter(description = "Filter by coupon status")
            @RequestParam(required = false) CouponStatus status,
            @Parameter(description = "Filter by fuel type ID")
            @RequestParam(required = false) Long fuelTypeId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        CouponFilterRequest filter = new CouponFilterRequest(null, null, fuelTypeId, status, locationId, null, null);
        return ResponseEntity.ok(ApiResponse.success(couponService.getCoupons(filter, pageable)));
    }
}