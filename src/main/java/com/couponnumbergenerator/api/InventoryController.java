package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.CouponFilterRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.dto.response.InventorySummaryResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.service.CouponService;
import com.couponnumbergenerator.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.INVENTORY_PATH)
@Tag(name = "Inventory", description = "Real-time stock visibility by location, fuel type and status")
public class InventoryController {

    private final InventoryService inventoryService;
    private final CouponService couponService;

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