package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.CompanyRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.CompanyResponse;
import com.couponnumbergenerator.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.COMPANIES_PATH)
@Tag(name = "Companies", description = "Who runs each site — Petrotrade or a third party redeeming on our behalf")
public class CompanyController {

    private final LocationService locationService;

    @GetMapping
    @Operation(summary = "All companies, by name")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(locationService.getCompanies()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','REGIONAL_REP','RETAIL_MANAGER')")
    @Operation(summary = "Create a company", description = "code (≤20, unique, upper-cased) and name (unique) are required.")
    public ResponseEntity<ApiResponse<CompanyResponse>> create(@Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Company created", locationService.createCompany(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REGIONAL_REP','RETAIL_MANAGER')")
    @Operation(summary = "Rename or (de)activate a company", description = "The code never changes.")
    public ResponseEntity<ApiResponse<CompanyResponse>> update(@PathVariable Long id, @Valid @RequestBody CompanyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company updated", locationService.updateCompany(id, request)));
    }
}
