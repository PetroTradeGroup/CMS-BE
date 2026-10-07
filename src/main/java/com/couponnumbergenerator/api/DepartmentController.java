package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.CreateDepartmentRequest;
import com.couponnumbergenerator.dto.request.UpdateDepartmentRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.DepartmentResponse;
import com.couponnumbergenerator.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.DEPARTMENTS_PATH)
@Tag(name = "Departments", description = "Organizational units coupons move through (e.g. Stocks, Commercial, Retail)")
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a department")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Department created successfully", departmentService.create(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SALES_CLERK','ACCOUNTS_CLERK')")
    @Operation(summary = "Get all departments, optionally filtered by active flag")
    public ResponseEntity<ApiResponse<List<DepartmentResponse>>> getAll(
            @Parameter(description = "Filter by active flag; omit for all departments")
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getAll(active)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALES_CLERK','ACCOUNTS_CLERK')")
    @Operation(summary = "Get department by ID")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a department", description = "Departments are never deleted; deactivate them instead.")
    public ResponseEntity<ApiResponse<DepartmentResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success("Department updated successfully", departmentService.update(id, request)));
    }
}
