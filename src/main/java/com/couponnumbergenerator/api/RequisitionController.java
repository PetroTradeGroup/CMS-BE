package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.AutoFulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.CreateRequisitionRequest;
import com.couponnumbergenerator.dto.request.FulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.RequisitionDecisionRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.AutoFulfillResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.REQUISITIONS_PATH)
@Tag(name = "Requisitions", description = "A department's request to Stock for coupons, in books per denomination "
        + "(a book = 100 coupons; litres are derived as books × 100 × denomination) — the digital Internal Purchase Requisition")
public class RequisitionController {

    private final CouponRequisitionService couponRequisitionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SALES_CLERK','ACCOUNTS_CLERK','ADMIN')")
    @Operation(summary = "Raise a requisition", description = "A department asks Stock for coupons in books, e.g. "
            + "200 books of 20L petrol + 10 books of 5L petrol — the system derives the litres (200×100×20 + "
            + "10×100×5). Each line names a fuelTypeId and either 'books' (preferred) or 'litres' (which must be "
            + "an exact whole-book multiple, else 400); a single requisition can mix fuel types (e.g. petrol and "
            + "diesel lines together), even at the same denomination.")
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
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
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

    @PostMapping("/{id}/auto-fulfill")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
    @Operation(summary = "Auto-fulfil a requisition in whole books, oldest batches first",
            description = "No batch or lines are named: for each outstanding line the system walks the batches of "
                    + "that line's fuel type oldest-first (FIFO) and draws whole books (100 coupons of the line's "
                    + "denomination) from each until the line is covered or stock runs out — spilling into the "
                    + "next batch automatically when one runs dry. One deferred transfer (supervisor approval) is "
                    + "raised per batch drawn from; litres credit on receipt confirmation, exactly as with manual "
                    + "fulfill. Litres short of a whole book stay outstanding. 400 if not a single whole book of "
                    + "issuable stock exists for any outstanding line.")
    public ResponseEntity<ApiResponse<AutoFulfillResponse>> autoFulfill(
            @PathVariable Long id, @Valid @RequestBody AutoFulfillRequisitionRequest request) {
        AutoFulfillResponse response = couponRequisitionService.autoFulfill(id, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
                "%d transfer request(s) submitted for supervisor approval".formatted(response.transfers().size()),
                response));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
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