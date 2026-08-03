package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.CreateRequisitionRequest;
import com.couponnumbergenerator.dto.request.FulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.RequisitionDecisionRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.RequisitionResponse;
import com.couponnumbergenerator.dto.response.TransferResultResponse;
import com.couponnumbergenerator.enums.RequisitionStatus;
import com.couponnumbergenerator.service.ActionOutcome;
import com.couponnumbergenerator.service.CouponRequisitionService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.REQUISITIONS_PATH)
@Tag(name = "Requisitions", description = "A department's request to Stock for coupons, in litres per denomination — the digital Internal Purchase Requisition")
public class RequisitionController {

    private final CouponRequisitionService couponRequisitionService;

    @PostMapping
    @Operation(summary = "Raise a requisition", description = "A department asks Stock for coupons, e.g. 200L of "
            + "20L petrol. Each line names a fuelTypeId — a single requisition can mix fuel types (e.g. petrol "
            + "and diesel lines together), even at the same denomination.")
    public ResponseEntity<ApiResponse<RequisitionResponse>> create(@Valid @RequestBody CreateRequisitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Requisition raised", couponRequisitionService.create(request)));
    }

    @GetMapping
    @Operation(summary = "The requisition queue, optionally filtered by status (defaults to all)")
    public ResponseEntity<ApiResponse<PagedResponse<RequisitionResponse>>> getRequisitions(
            @Parameter(description = "Filter by status: PENDING, PARTIALLY_FULFILLED, FULFILLED, REJECTED")
            @RequestParam(required = false) RequisitionStatus status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(couponRequisitionService.getRequisitions(status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a requisition by ID")
    public ResponseEntity<ApiResponse<RequisitionResponse>> getRequisition(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(couponRequisitionService.getRequisition(id)));
    }

    @PostMapping("/{id}/fulfill")
    @Operation(summary = "Fulfil a requisition, in whole or in part",
            description = "Stock names the batchId to draw from and how many litres of each fuel type + "
                    + "denomination it's issuing right now — which may be less than what's outstanding on the "
                    + "requisition (a partial delivery/drawdown; the remainder stays open for a later fulfill "
                    + "call, against this or another batch). Since a batch is always a single fuel type, every "
                    + "line in one fulfill call must match the named batch's fuel type (400 if not) — a "
                    + "requisition mixing petrol and diesel needs one fulfill call per fuel type, each against a "
                    + "matching batch (GET /batches?fuelTypeId=&hasStock=true finds one). If the named batch "
                    + "doesn't have enough eligible coupons of a requested denomination on hand, it still issues "
                    + "whatever it does have rather than rejecting the whole call — only fails 400 if the batch "
                    + "has zero eligible coupons for every requested line. Builds and submits the underlying "
                    + "transfer automatically (see POST /coupons/transfers); the resulting request always defers "
                    + "for supervisor approval since it moves department. The requisition's lines only credit the "
                    + "litres actually delivered once the receiving department confirms receipt "
                    + "(POST /approvals/{id}/confirm-receipt) — it flips to FULFILLED once every line is "
                    + "satisfied, or PARTIALLY_FULFILLED (still open to further fulfill/reject calls) if not.")
    public ResponseEntity<ApiResponse<?>> fulfill(@PathVariable Long id, @Valid @RequestBody FulfillRequisitionRequest request) {
        ActionOutcome<TransferResultResponse> outcome = couponRequisitionService.fulfill(id, request);
        return switch (outcome) {
            case ActionOutcome.Applied<TransferResultResponse> applied -> ResponseEntity.ok(ApiResponse.success(
                    "%d coupon(s) transferred".formatted(applied.result().count()), applied.result()));
            case ActionOutcome.Pending<TransferResultResponse> pending -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(
                            "Submitted for supervisor approval (request #%d)".formatted(pending.request().id()),
                            pending.request()));
        };
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a requisition, or close out what's left of a partial one",
            description = "A reason is required. Valid while PENDING or PARTIALLY_FULFILLED — rejecting a "
                    + "partially-fulfilled requisition closes out the outstanding balance without undoing "
                    + "coupons already delivered.")
    public ResponseEntity<ApiResponse<RequisitionResponse>> reject(
            @PathVariable Long id, @Valid @RequestBody RequisitionDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Requisition %d rejected".formatted(id),
                couponRequisitionService.reject(id, request)));
    }
}