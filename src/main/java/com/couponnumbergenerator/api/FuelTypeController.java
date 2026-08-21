package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.CreateFuelTypeRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.FuelTypeResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.service.FuelTypeService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping(CouponConstants.API_BASE_PATH + "/fuel-types")
@Tag(name = "Fuel Types", description = "Manage configurable fuel types")
public class FuelTypeController {

    private final FuelTypeService fuelTypeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new fuel type")
    public ResponseEntity<ApiResponse<FuelTypeResponse>> create(@Valid @RequestBody CreateFuelTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fuel type created successfully", fuelTypeService.create(request)));
    }

    @GetMapping
    @Operation(summary = "Get all fuel types")
    public ResponseEntity<ApiResponse<PagedResponse<FuelTypeResponse>>> getAll(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(fuelTypeService.getAll(pageable)));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active fuel types only")
    public ResponseEntity<ApiResponse<PagedResponse<FuelTypeResponse>>> getAllActive(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(fuelTypeService.getAllActive(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get fuel type by ID")
    public ResponseEntity<ApiResponse<FuelTypeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(fuelTypeService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a fuel type")
    public ResponseEntity<ApiResponse<FuelTypeResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CreateFuelTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Fuel type updated successfully", fuelTypeService.update(id, request)));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate a fuel type")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable Long id) {
        fuelTypeService.activate(id);
        return ResponseEntity.ok(ApiResponse.success("Fuel type activated", null));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a fuel type")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        fuelTypeService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success("Fuel type deactivated", null));
    }
}