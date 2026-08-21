package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.UpdateBulkLimitRequest;
import com.couponnumbergenerator.dto.request.UpdatePageSizeLimitRequest;
import com.couponnumbergenerator.dto.request.UpdateValidityPeriodRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.BulkLimitResponse;
import com.couponnumbergenerator.dto.response.PageSizeLimitResponse;
import com.couponnumbergenerator.dto.response.ValidityPeriodResponse;
import com.couponnumbergenerator.service.BulkConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + "/config")
@Tag(name = "Configuration", description = "Runtime configuration management")
@PreAuthorize("hasRole('ADMIN')")
public class BulkConfigController {

    private final BulkConfigService bulkConfigService;

    @GetMapping("/bulk-limit")
    @Operation(summary = "Get current bulk generation limit")
    public ResponseEntity<ApiResponse<BulkLimitResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(bulkConfigService.get()));
    }

    @PatchMapping("/bulk-limit")
    @Operation(summary = "Update bulk generation limit", description = "Changes take effect immediately for all subsequent requests")
    public ResponseEntity<ApiResponse<BulkLimitResponse>> update(@Valid @RequestBody UpdateBulkLimitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Bulk limit updated successfully", bulkConfigService.update(request)));
    }

    @GetMapping("/page-size-limit")
    @Operation(summary = "Get current page size limit")
    public ResponseEntity<ApiResponse<PageSizeLimitResponse>> getPageSizeLimit() {
        return ResponseEntity.ok(ApiResponse.success(bulkConfigService.getPageSizeLimit()));
    }

    @PatchMapping("/page-size-limit")
    @Operation(summary = "Update page size limit", description = "Caps the maximum number of records returned per page. Changes take effect immediately.")
    public ResponseEntity<ApiResponse<PageSizeLimitResponse>> updatePageSizeLimit(@Valid @RequestBody UpdatePageSizeLimitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Page size limit updated successfully", bulkConfigService.updatePageSizeLimit(request)));
    }

    @GetMapping("/validity-period")
    @Operation(summary = "Get default coupon validity period",
            description = "Days a coupon stays valid after generation when no explicit expiryDate is supplied. Default: 365.")
    public ResponseEntity<ApiResponse<ValidityPeriodResponse>> getValidityPeriod() {
        return ResponseEntity.ok(ApiResponse.success(bulkConfigService.getValidityPeriod()));
    }

    @PatchMapping("/validity-period")
    @Operation(summary = "Update default coupon validity period",
            description = "Applies to coupons generated after the change; existing coupons keep their expiry dates. "
                    + "An explicit expiryDate on a generate request always overrides this default.")
    public ResponseEntity<ApiResponse<ValidityPeriodResponse>> updateValidityPeriod(
            @Valid @RequestBody UpdateValidityPeriodRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Validity period updated successfully",
                bulkConfigService.updateValidityPeriod(request)));
    }
}