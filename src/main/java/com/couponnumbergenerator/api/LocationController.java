package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.AssignCompanyRequest;
import com.couponnumbergenerator.dto.request.CreateLocationRequest;
import com.couponnumbergenerator.dto.request.UpdateLocationRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.LocationResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.LocationType;
import com.couponnumbergenerator.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.LOCATIONS_PATH)
@Tag(name = "Locations", description = "Depots and retail sites where coupon stock is held")
public class LocationController {

    private final LocationService locationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a location")
    public ResponseEntity<ApiResponse<LocationResponse>> create(
            @Valid @RequestBody CreateLocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Location created successfully", locationService.create(request)));
    }

    @GetMapping
    @Operation(summary = "Get locations, optionally filtered by active flag, type, company and code/name search")
    public ResponseEntity<ApiResponse<PagedResponse<LocationResponse>>> getAll(
            @Parameter(description = "Filter by active flag; omit for all locations")
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) LocationType type,
            @RequestParam(required = false) Long companyId,
            @Parameter(description = "Case-insensitive match on code or name")
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(locationService.getAll(active, type, companyId, q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get location by ID")
    public ResponseEntity<ApiResponse<LocationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(locationService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a location", description = "Locations are never deleted; deactivate them instead.")
    public ResponseEntity<ApiResponse<LocationResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLocationRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Location updated successfully", locationService.update(id, request)));
    }

    @PutMapping("/{id}/company")
    @PreAuthorize("hasAnyRole('ADMIN','REGIONAL_REP','RETAIL_MANAGER')")
    @Operation(summary = "Put a site under a company",
            description = "Its redemptions are reported under that company from then on (reports read the current assignment).")
    public ResponseEntity<ApiResponse<LocationResponse>> assignCompany(
            @PathVariable Long id, @Valid @RequestBody AssignCompanyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company assigned", locationService.assignCompany(id, request.companyId())));
    }
}