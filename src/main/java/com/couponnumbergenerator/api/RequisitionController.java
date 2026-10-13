package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.AutoFulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.CreateRequisitionRequest;
import com.couponnumbergenerator.dto.request.FulfillRequisitionRequest;
import com.couponnumbergenerator.dto.request.RequisitionDecisionRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.AutoFulfillResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.RequisitionResponse;
import com.couponnumbergenerator.enums.RequisitionStatus;
import com.couponnumbergenerator.exception.RequisitionNotFoundException;
import com.couponnumbergenerator.security.LocationAccessGuard;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.REQUISITIONS_PATH)
@Tag(name = "Requisitions", description = "A department's request to Stock for coupons, in books per denomination "
        + "(a book = 100 coupons; litres are derived as books × 100 × denomination) — the digital Internal Purchase Requisition")
public class RequisitionController {

    private final CouponRequisitionService couponRequisitionService;
    private final LocationAccessGuard locationAccessGuard;

    @PostMapping
    @PreAuthorize("hasAnyRole('SALES_CLERK','ACCOUNTS_CLERK','ADMIN')")
    @Operation(summary = "Raise a requisition", description = "A department asks Stock for coupons in books, e.g. "
            + "200 books of 20L petrol + 10 books of 5L petrol — the system derives the litres (200×100×20 + "
            + "10×100×5). Each line names a fuelTypeId and either 'books' (preferred) or 'litres' (which must be "
            + "an exact whole-book multiple, else 400); a single requisition can mix fuel types (e.g. petrol and "
            + "diesel lines together), even at the same denomination.")
    public ResponseEntity<ApiResponse<RequisitionResponse>> create(
            @Valid @RequestBody CreateRequisitionRequest request, Authentication authentication) {
        // requestedBy comes from the token, never the body — it's what a Sales Clerk's "own requisitions" view filters on.
        CreateRequisitionRequest stamped = new CreateRequisitionRequest(request.departmentId(), request.locationId(),
                locationAccessGuard.callerUsername(authentication), request.lines());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Requisition raised",
                        couponRequisitionService.create(stamped, ((Jwt) authentication.getPrincipal()).getClaimAsString("department"))));
    }

    @GetMapping
    @Operation(summary = "The requisition queue, optionally filtered by status (defaults to all)",
            description = "A Sales Clerk only sees the requisitions they raised themselves.")
    public ResponseEntity<ApiResponse<PagedResponse<RequisitionResponse>>> getRequisitions(
            @Parameter(description = "Filter by status: PENDING, PARTIALLY_FULFILLED, FULFILLED, REJECTED")
            @RequestParam(required = false) RequisitionStatus status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                couponRequisitionService.getRequisitions(status, ownerFilter(authentication), pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a requisition by ID", description = "404 for a Sales Clerk asking for someone else's requisition.")
    public ResponseEntity<ApiResponse<RequisitionResponse>> getRequisition(@PathVariable Long id, Authentication authentication) {
        RequisitionResponse response = couponRequisitionService.getRequisition(id);
        String owner = ownerFilter(authentication);
        if (owner != null && !owner.equals(response.requestedBy())) {
            throw new RequisitionNotFoundException(id);
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** A Sales Clerk is limited to their own requisitions; anyone with a broader role (Stocks, Admin, …) sees them all. */
    private String ownerFilter(Authentication authentication) {
        return locationAccessGuard.salesClerkOnlyUsername(authentication);
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
                    + "has zero eligible coupons for every requested line. Only valid once the requisition is "
                    + "APPROVED (both Commercial and the Stocks Controller signed off) or PARTIALLY_FULFILLED — "
                    + "409 otherwise. This is the issue (GIV): the underlying transfer is built and, since the "
                    + "Stocks Controller already approved the requisition, goes straight to TRANSFERSHIPMENT "
                    + "(coupons IN_TRANSIT) with no second approval. The requisition's lines only credit the "
                    + "litres actually delivered once the receiving Sales Clerk confirms receipt "
                    + "(POST /approvals/{id}/confirm-receipt, the GRV) — it flips to FULFILLED once every line "
                    + "is satisfied, or PARTIALLY_FULFILLED (still open to further fulfill/reject calls) if not.")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> fulfill(
            @PathVariable Long id, @Valid @RequestBody FulfillRequisitionRequest request) {
        ApprovalRequestResponse issued = couponRequisitionService.fulfill(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                "Issued under GIV #%d — in transit, awaiting receipt".formatted(issued.id()), issued));
    }

    @PostMapping("/{id}/auto-fulfill")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
    @Operation(summary = "Auto-fulfil a requisition in whole books, oldest batches first",
            description = "No batch or lines are named: for each outstanding line the system walks the batches of "
                    + "that line's fuel type oldest-first (FIFO) and draws whole books (100 coupons of the line's "
                    + "denomination) from each until the line is covered or stock runs out — spilling into the "
                    + "next batch automatically when one runs dry. One transfer (GIV), issued straight to "
                    + "IN_TRANSIT, is raised per batch drawn from; litres credit on receipt confirmation, exactly "
                    + "as with manual fulfill. Same APPROVED/PARTIALLY_FULFILLED precondition as fulfill. Litres "
                    + "short of a whole book stay outstanding. 400 if not a single whole book of issuable stock "
                    + "exists for any outstanding line.")
    public ResponseEntity<ApiResponse<AutoFulfillResponse>> autoFulfill(
            @PathVariable Long id, @Valid @RequestBody AutoFulfillRequisitionRequest request) {
        AutoFulfillResponse response = couponRequisitionService.autoFulfill(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                "%d issue(s) in transit, awaiting receipt".formatted(response.transfers().size()), response));
    }

    @PostMapping("/{id}/commercial-approve")
    @PreAuthorize("hasAnyRole('COMMERCIAL_MANAGER','REGIONAL_REP')")
    @Operation(summary = "Commercial's approval — the first sign-off",
            description = "The Commercial Manager or Rep approves a PENDING requisition raised by a Sales Clerk; "
                    + "it moves to COMMERCIAL_APPROVED and on to the Stocks Controller. No body — the approver "
                    + "is taken from the token. 409 unless PENDING.")
    public ResponseEntity<ApiResponse<RequisitionResponse>> approveCommercial(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Requisition %d approved by Commercial".formatted(id),
                couponRequisitionService.approveCommercial(id, locationAccessGuard.callerUsername(authentication))));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('STOCKS_CONTROLLER')")
    @Operation(summary = "Stocks Controller's approval — the second sign-off",
            description = "Approves a COMMERCIAL_APPROVED requisition; it moves to APPROVED and a Stocks Clerk can "
                    + "now issue against it (fulfill / auto-fulfill). No body — the approver is taken from the "
                    + "token. 409 unless COMMERCIAL_APPROVED.")
    public ResponseEntity<ApiResponse<RequisitionResponse>> approveStocks(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Requisition %d approved by Stocks".formatted(id),
                couponRequisitionService.approveStocks(id, locationAccessGuard.callerUsername(authentication))));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('STOCKS_CONTROLLER','COMMERCIAL_MANAGER','REGIONAL_REP')")
    @Operation(summary = "Reject a requisition, or close out what's left of a partial one",
            description = "A reason is required. Whoever's turn it is rejects: Commercial (Manager or Rep) while "
                    + "PENDING, the Stocks Controller from COMMERCIAL_APPROVED onwards (403 otherwise). Rejecting a "
                    + "partially-fulfilled requisition closes out the outstanding balance without undoing coupons "
                    + "already delivered.")
    public ResponseEntity<ApiResponse<RequisitionResponse>> reject(
            @PathVariable Long id, @Valid @RequestBody RequisitionDecisionRequest request, Authentication authentication) {
        boolean commercialStage = couponRequisitionService.getRequisition(id).status() == RequisitionStatus.PENDING;
        Set<String> allowed = commercialStage
                ? Set.of("ROLE_COMMERCIAL_MANAGER", "ROLE_REGIONAL_REP")
                : Set.of("ROLE_STOCKS_CONTROLLER");
        if (authentication.getAuthorities().stream().noneMatch(a -> allowed.contains(a.getAuthority()))) {
            throw new AccessDeniedException(commercialStage
                    ? "Only Commercial can reject a requisition awaiting Commercial's approval"
                    : "Only a Stocks Controller can reject a requisition once Commercial has approved it");
        }
        return ResponseEntity.ok(ApiResponse.success("Requisition %d rejected".formatted(id),
                couponRequisitionService.reject(id, request)));
    }
}