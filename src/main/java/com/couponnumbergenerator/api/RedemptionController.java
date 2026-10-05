package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.RedemptionPostRequest;
import com.couponnumbergenerator.dto.request.RedemptionSubmitRequest;
import com.couponnumbergenerator.dto.request.ScanRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.ApprovalRequestResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.dto.response.RedemptionByAttendantResponse;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse;
import com.couponnumbergenerator.dto.response.ScanResponse;
import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.exception.ApprovalRequestNotFoundException;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.QrCodeService;
import com.couponnumbergenerator.service.RedemptionReportService;
import com.couponnumbergenerator.service.RedemptionSummaryExportService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

@Validated
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.REDEMPTIONS_PATH)
@Tag(name = "Redemptions", description = "Redeeming coupons at a site: scan/submit, then post with a Navision document number")
public class RedemptionController {

    private final CouponLifecycleService couponLifecycleService;
    private final RedemptionReportService redemptionReportService;
    private final RedemptionSummaryExportService redemptionSummaryExportService;
    private final QrCodeService qrCodeService;

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ATTENDANT','TEAM_LEADER')")
    @Operation(summary = "Preview a scanned coupon before redeeming it",
            description = "Verifies the scanned QR payload's HMAC signature (400 if forged or corrupted), "
                    + "looks the coupon up and returns its full details — number, denomination, fuel type, "
                    + "status, location, expiry — plus whether it can be redeemed right now and, if not, a "
                    + "plain-language reason. Read-only: nothing changes until the batch is submitted via "
                    + "POST /redemptions.")
    public ResponseEntity<ApiResponse<ScanResponse>> scan(@Valid @RequestBody ScanRequest request) {
        String couponNumber = qrCodeService.decodeAndVerify(request.payload());
        return ResponseEntity.ok(ApiResponse.success(
                couponLifecycleService.previewRedemption(couponNumber)));
    }

    @GetMapping("/scan/{couponNumber}")
    @PreAuthorize("hasAnyRole('ATTENDANT','TEAM_LEADER')")
    @Operation(summary = "Preview a manually-typed coupon number before redeeming it",
            description = "Same as POST /scan, but for a coupon number typed by hand instead of a scanned QR "
                    + "payload — the fallback for a damaged QR. No signature to verify (there isn't one for a "
                    + "typed number), so an unknown number is 404 rather than the 400 a forged/corrupted QR "
                    + "gets. Read-only: nothing changes until the batch is submitted via POST /redemptions.")
    public ResponseEntity<ApiResponse<ScanResponse>> scanByCouponNumber(@PathVariable String couponNumber) {
        return ResponseEntity.ok(ApiResponse.success(
                couponLifecycleService.previewRedemption(couponNumber)));
    }

    @GetMapping("/scan/code/{redemptionCode}")
    @PreAuthorize("hasAnyRole('ATTENDANT','TEAM_LEADER','ADMIN')")
    @Operation(summary = "Verify a virtual coupon's 7-character redemption code",
            description = "Same as POST /scan, for a virtual coupon whose QR can't be scanned: the coupon (fuel type, "
                    + "litres, expiry, status) and whether it can be redeemed right now — false with the reason if "
                    + "used, cancelled, expired or already submitted elsewhere. Case, spaces and hyphens are "
                    + "ignored; an unknown code is 404. Read-only. Attendant/Team Leader verify before dispensing "
                    + "(then submit via POST /redemptions); ADMIN may check a code but can't redeem it.")
    public ResponseEntity<ApiResponse<ScanResponse>> scanByRedemptionCode(@PathVariable String redemptionCode) {
        return ResponseEntity.ok(ApiResponse.success(
                couponLifecycleService.previewRedemptionByCode(redemptionCode)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ATTENDANT','TEAM_LEADER','ADMIN','STOCKS_CLERK','STOCKS_CONTROLLER','REGIONAL_REP')")
    @Operation(summary = "What was redeemed over a day or a date range",
            description = "Coupon count and litres, in total and per fuel type (with a denomination "
                    + "breakdown) — optionally limited to one site and/or one fuel type. Pass dateFrom/dateTo "
                    + "for a range, or date alone for a single day; with none given, defaults to today. For a "
                    + "station-scoped caller (Attendant/Team Leader), locationId is ignored — the site comes "
                    + "from their token — and a plain Attendant only sees what they personally redeemed.")
    public ResponseEntity<ApiResponse<RedemptionSummaryResponse>> summary(
            @Parameter(description = "Single day (ISO format, e.g. 2026-08-05) — shorthand for dateFrom=dateTo=date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Start of the range (inclusive); defaults to dateTo, or today if neither given")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "End of the range (inclusive); defaults to dateFrom, or today if neither given")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Limit to one site (location ID) — ignored for a station-scoped caller")
            @RequestParam(required = false) Long locationId,
            @Parameter(description = "Limit to one fuel type")
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate[] range = resolveRange(date, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.success(
                redemptionReportService.summary(range[0], range[1], locationId, fuelTypeId)));
    }

    @GetMapping("/summary/export/excel")
    @Operation(summary = "Export the redemption summary as an Excel workbook",
            description = "Same filters and scoping as GET /summary; one row per fuel type/denomination, "
                    + "with fuel-type subtotals and a grand total.")
    public ResponseEntity<StreamingResponseBody> exportSummaryExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate[] range = resolveRange(date, dateFrom, dateTo);
        RedemptionSummaryResponse summary = redemptionReportService.summary(range[0], range[1], locationId, fuelTypeId);
        StreamingResponseBody body = out -> redemptionSummaryExportService.exportExcel(summary, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"redemption-summary-%s.xlsx\"".formatted(exportTimestamp()))
                .contentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/summary/export/pdf")
    @Operation(summary = "Export the redemption summary as a PDF",
            description = "Same filters and scoping as GET /summary; one row per fuel type/denomination, "
                    + "with fuel-type subtotals and a grand total.")
    public ResponseEntity<StreamingResponseBody> exportSummaryPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate[] range = resolveRange(date, dateFrom, dateTo);
        RedemptionSummaryResponse summary = redemptionReportService.summary(range[0], range[1], locationId, fuelTypeId);
        StreamingResponseBody body = out -> redemptionSummaryExportService.exportPdf(summary, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"redemption-summary-%s.pdf\"".formatted(exportTimestamp()))
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    private String exportTimestamp() {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    }

    /** attendantUsername is caller-supplied and lands in a Content-Disposition header value — strip anything but safe filename characters. */
    private String sanitizeForFilename(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @GetMapping("/summary/by-attendant")
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @Operation(summary = "Who redeemed what, over a day or a date range",
            description = "Per-attendant coupon count and litres at the caller's own station, optionally "
                    + "narrowed to one attendant and/or one fuel type. Team Leader only — which attendant "
                    + "redeemed what is their information to see, not Admin's; Admin gets site-level totals "
                    + "via GET /summary instead, with no individual attendant names.")
    public ResponseEntity<ApiResponse<RedemptionByAttendantResponse>> summaryByAttendant(
            @Parameter(description = "Single day (ISO format, e.g. 2026-08-05) — shorthand for dateFrom=dateTo=date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Start of the range (inclusive); defaults to dateTo, or today if neither given")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "End of the range (inclusive); defaults to dateFrom, or today if neither given")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Limit to one attendant (their Keycloak username) at the caller's station")
            @RequestParam(required = false) String attendantUsername,
            @Parameter(description = "Limit to one fuel type")
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate[] range = resolveRange(date, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.success(
                redemptionReportService.byAttendant(range[0], range[1], attendantUsername, fuelTypeId)));
    }

    @GetMapping("/summary/by-attendant/{attendantUsername}")
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @Operation(summary = "What one attendant redeemed, over a day or a date range",
            description = "Same fuel type/denomination breakdown as GET /summary, narrowed to a single "
                    + "attendant at the caller's own station. Team Leader only. Use this after GET "
                    + "/summary/by-attendant to drill into one name from that list.")
    public ResponseEntity<ApiResponse<RedemptionSummaryResponse>> summaryForAttendant(
            @PathVariable String attendantUsername,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Limit to one fuel type")
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate[] range = resolveRange(date, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.success(
                redemptionReportService.summaryForAttendant(range[0], range[1], attendantUsername, fuelTypeId)));
    }

    @GetMapping("/summary/by-attendant/{attendantUsername}/export/excel")
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @Operation(summary = "Export one attendant's redemption summary as an Excel workbook",
            description = "Same data and scoping as GET /summary/by-attendant/{attendantUsername}.")
    public ResponseEntity<StreamingResponseBody> exportSummaryForAttendantExcel(
            @PathVariable String attendantUsername,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate[] range = resolveRange(date, dateFrom, dateTo);
        RedemptionSummaryResponse summary =
                redemptionReportService.summaryForAttendant(range[0], range[1], attendantUsername, fuelTypeId);
        StreamingResponseBody body = out -> redemptionSummaryExportService.exportExcel(summary, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"redemption-%s-%s.xlsx\"".formatted(sanitizeForFilename(attendantUsername), exportTimestamp()))
                .contentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/summary/by-attendant/{attendantUsername}/export/pdf")
    @PreAuthorize("hasRole('TEAM_LEADER')")
    @Operation(summary = "Export one attendant's redemption summary as a PDF",
            description = "Same data and scoping as GET /summary/by-attendant/{attendantUsername}.")
    public ResponseEntity<StreamingResponseBody> exportSummaryForAttendantPdf(
            @PathVariable String attendantUsername,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long fuelTypeId) {
        LocalDate[] range = resolveRange(date, dateFrom, dateTo);
        RedemptionSummaryResponse summary =
                redemptionReportService.summaryForAttendant(range[0], range[1], attendantUsername, fuelTypeId);
        StreamingResponseBody body = out -> redemptionSummaryExportService.exportPdf(summary, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"redemption-%s-%s.pdf\"".formatted(sanitizeForFilename(attendantUsername), exportTimestamp()))
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    /** {date} alone is single-day shorthand; an open-ended dateFrom/dateTo defaults its other end to itself; nothing given defaults both to today. */
    private LocalDate[] resolveRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null || dateTo != null) {
            LocalDate from = dateFrom != null ? dateFrom : dateTo;
            LocalDate to = dateTo != null ? dateTo : dateFrom;
            return new LocalDate[]{from, to};
        }
        LocalDate day = date != null ? date : LocalDate.now();
        return new LocalDate[]{day, day};
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
    @PreAuthorize("hasAnyRole('ATTENDANT','TEAM_LEADER')")
    @Operation(summary = "Submit a batch of coupons for redemption",
            description = "Digital equivalent of counting/signing the redemption form and scan-verifying it "
                    + "(Duties 4-5). Resolve coupons from scanned, HMAC-signed QR payloads (scannedPayloads — "
                    + "verified against tampering), manually-typed coupon numbers (fallback for a damaged "
                    + "QR — physical coupons only, 400 for a virtual one) and/or redemptionCodes (the 7-character "
                    + "code on a virtual coupon); at least one is required. A coupon already in a PENDING "
                    + "redemption is refused with 409 — it was presented elsewhere, don't dispense. No location check against current stock — a coupon is only "
                    + "redeemable once ALLOCATED (sold to a customer), and a sold coupon can be redeemed at any "
                    + "site, not just wherever it happened to be stocked. Attendant/Team Leader only: the "
                    + "redeeming site and identity come from the token, not the request body (see "
                    + "LocationAccessGuard), and an account with no station is 403. An expired coupon is 400. "
                    + "carRegistrationNumber is always required — the vehicle this redemption was for. Always "
                    + "deferred (202 + PENDING): after commit the redemption is automatically posted to the ERP in "
                    + "the background (retried periodically if the ERP is down); POST /{id}/post remains as the "
                    + "manual fallback.")
    public ResponseEntity<ApiResponse<ApprovalRequestResponse>> submit(@Valid @RequestBody RedemptionSubmitRequest request) {
        ApprovalRequestResponse response = couponLifecycleService.submitRedemption(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Submitted for redemption (request #%d)".formatted(response.id()), response));
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAnyRole('STOCKS_CLERK','STOCKS_CONTROLLER','ADMIN')")
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
