package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.ApprovalDecisionRequest;
import com.couponnumbergenerator.dto.request.ReceiptConfirmationRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.service.CouponLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.APPROVALS_PATH)
@Tag(name = "Approvals", description = "Supervisor sign-off queue for coupon transitions/transfers that move location or department")
public class ApprovalController {

    private final CouponLifecycleService couponLifecycleService;

    @GetMapping
    @Operation(summary = "The approval queue, optionally filtered by status (defaults to all)")
    public ResponseEntity<ApiResponse<PagedResponse<ApprovalRequestResponse>>> getApprovalRequests(
            @Parameter(description = "Filter by status: PENDING, APPROVED, TRANSFERSHIPMENT, TRANSRECEIPT, POSTED, REJECTED")
            @RequestParam(required = false) ApprovalStatus status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(couponLifecycleService.getApprovalRequests(status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an approval request by ID")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> getApprovalRequest(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(couponLifecycleService.getApprovalRequest(id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('STOCKS_CONTROLLER','SALES_EXECUTIVE','ADMIN')")
    @Operation(summary = "Approve a pending request",
            description = "Executes the move, re-validating against each coupon's current state. For a TRANSFER "
                    + "that changes department, this only moves the coupons to IN_TRANSIT and the approval request "
                    + "to TRANSFERSHIPMENT (the Stock supervisor's sign-off, like signing a GIV) — the real target "
                    + "status and toLocation/toDepartment aren't applied until the receiving department confirms "
                    + "via POST /{id}/confirm-receipt. Everything else (TRANSITION, or a TRANSFER with only "
                    + "toLocationId) applies fully here, as before. Returns 409 if the request was already "
                    + "decided, or if a coupon's state drifted since it was requested.")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> approve(
            @PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Approval request %d approved".formatted(id),
                couponLifecycleService.approve(id, request)));
    }

    @PostMapping("/{id}/confirm-receipt")
    @PreAuthorize("hasAnyRole('STOCKS_CONTROLLER','SALES_EXECUTIVE','ADMIN')")
    @Operation(summary = "Confirm receipt of a TRANSFERSHIPMENT department-handoff transfer",
            description = "The receiving department's signature that the coupons actually arrived — the digital "
                    + "equivalent of the paper GRV/GIV \"Goods Received By\" line. Applies the transfer's real "
                    + "target status (or IN_STOCK if none was requested) and moves the coupons into the "
                    + "toLocation/toDepartment, re-validating against each coupon's current state. Only valid "
                    + "while the request is TRANSFERSHIPMENT — returns 409 otherwise. On success the approval "
                    + "request's status becomes TRANSRECEIPT.")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> confirmReceipt(
            @PathVariable Long id, @Valid @RequestBody ReceiptConfirmationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Approval request %d received".formatted(id),
                couponLifecycleService.confirmReceipt(id, request)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STOCKS_CONTROLLER','SALES_EXECUTIVE','ADMIN')")
    @Operation(summary = "Reject a pending request", description = "No coupons are touched. A reason is required.")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> reject(
            @PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Approval request %d rejected".formatted(id),
                couponLifecycleService.reject(id, request)));
    }
}
