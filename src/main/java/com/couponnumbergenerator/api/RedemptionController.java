package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.RedemptionPostRequest;
import com.couponnumbergenerator.dto.request.RedemptionSubmitRequest;
import com.couponnumbergenerator.dto.request.ScanRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.RedemptionDailySummaryResponse;
import com.couponnumbergenerator.dto.response.ScanResponse;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.exception.ApprovalRequestNotFoundException;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.CouponService;
import com.couponnumbergenerator.service.QrCodeService;
import com.couponnumbergenerator.service.RedemptionReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.REDEMPTIONS_PATH)
@Tag(name = "Redemptions", description = "Redeeming coupons at a site: scan/submit, then post with a Navision document number")
public class RedemptionController {

    private final CouponLifecycleService couponLifecycleService;
    private final RedemptionReportService redemptionReportService;
    private final CouponService couponService;
    private final QrCodeService qrCodeService;

    @PostMapping("/scan")
    @Operation(summary = "Preview a scanned coupon before redeeming it",
            description = "Verifies the scanned QR payload's HMAC signature (400 if forged or corrupted), "
                    + "looks the coupon up and returns its full details — number, denomination, fuel type, "
                    + "status, location, expiry — plus whether it can be redeemed right now and, if not, a "
                    + "plain-language reason. Read-only: nothing changes until the batch is submitted via "
                    + "POST /redemptions.")
    public ResponseEntity<ApiResponse<ScanResponse>> scan(@Valid @RequestBody ScanRequest request) {
        String couponNumber = qrCodeService.decodeAndVerify(request.payload());
        return ResponseEntity.ok(ApiResponse.success(
                ScanResponse.of(couponService.getCouponByNumber(couponNumber))));
    }

    @GetMapping("/summary")
    @Operation(summary = "What was redeemed on a given day",
            description = "Coupon count and litres, in total and per fuel type (with a denomination "
                    + "breakdown), for the redemptions of one day — optionally limited to one site. "
                    + "Defaults to today.")
    public ResponseEntity<ApiResponse<RedemptionDailySummaryResponse>> summary(
            @Parameter(description = "The day to report on (ISO format, e.g. 2026-08-05); defaults to today")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Limit to one site (location ID)")
            @RequestParam(required = false) Long locationId) {
        LocalDate day = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(ApiResponse.success(redemptionReportService.dailySummary(day, locationId)));
    }

    @GetMapping
    @Operation(summary = "The redemption queue, optionally filtered by status (defaults to all)")
    public ResponseEntity<ApiResponse<PagedResponse<ApprovalRequestResponse>>> getRedemptions(
            @Parameter(description = "Filter by status: PENDING, POSTED, REJECTED")
            @RequestParam(required = false) ApprovalStatus status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                couponLifecycleService.getApprovalRequests(ApprovalRequestType.REDEMPTION, status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a redemption request by ID")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> getRedemption(@PathVariable Long id) {
        ApprovalRequestResponse response = couponLifecycleService.getApprovalRequest(id);
        if (response.requestType() != ApprovalRequestType.REDEMPTION) {
            throw new ApprovalRequestNotFoundException(id);
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @Operation(summary = "Submit a batch of coupons for redemption",
            description = "Digital equivalent of counting/signing the redemption form and scan-verifying it "
                    + "(Duties 4-5). Resolve coupons from scanned, HMAC-signed QR payloads (scannedPayloads — "
                    + "verified against tampering) and/or manually-typed coupon numbers (fallback for a damaged "
                    + "QR); at least one is required. Every resolved coupon must currently be at locationId — the "
                    + "site asserting the redemption — since there's no authenticated session yet to derive that "
                    + "automatically. Always deferred (202 + PENDING): after commit the redemption is automatically "
                    + "posted to the ERP in the background (retried periodically if the ERP is down); "
                    + "POST /{id}/post remains as the manual fallback.")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> submit(@Valid @RequestBody RedemptionSubmitRequest request) {
        ApprovalRequestResponse response = couponLifecycleService.submitRedemption(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Submitted for redemption (request #%d)".formatted(response.id()), response));
    }

    @PostMapping("/{id}/post")
    @Operation(summary = "Manually post a pending redemption with a Navision document number",
            description = "Duty 6: records the redemption with its ERP document number, re-validating each "
                    + "coupon is still ALLOCATED before flipping it to REDEEMED (409, all-or-nothing, if anything "
                    + "drifted since submission). Only valid while the request is PENDING. Normally the ERP "
                    + "auto-post does this in the background — this endpoint is the fallback when the document "
                    + "was raised in Navision by hand.")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> post(
            @PathVariable Long id, @Valid @RequestBody RedemptionPostRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Redemption request %d posted".formatted(id),
                couponLifecycleService.postRedemption(id, request)));
    }
}
