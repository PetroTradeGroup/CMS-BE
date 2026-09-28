package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.AuditFilterRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.AuditEventResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.AuditCategory;
import com.couponnumbergenerator.service.AuditExportService;
import com.couponnumbergenerator.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.AUDIT_PATH)
@Tag(name = "Audit", description = "System-wide audit trail: coupon lifecycle events, approval decisions, and security events (denied/unauthenticated requests, allowed mutations), merged into one chronological feed.")
public class AuditController {

    private final AuditService auditService;
    private final AuditExportService auditExportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @Operation(summary = "The system-wide audit feed, newest first",
            description = "Merges every coupon lifecycle event (generate/transfer/approve/allocate/redeem/legacy-"
                    + "import — from CouponMovement), every decided approval request (including rejections, "
                    + "which move no coupon and so leave no movement of their own), and every security event "
                    + "(a denied or unauthenticated request, or an allowed mutating one — GETs that succeed "
                    + "aren't logged). Sorted newest first; not affected by the sort field of the page "
                    + "parameter, only its page/size.")
    public ResponseEntity<ApiResponse<PagedResponse<AuditEventResponse>>> getAuditEvents(
            @Parameter(description = "Filter by event date (from), format: yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Filter by event date (to), format: yyyy-MM-dd")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @Parameter(description = "Filter by who performed/decided the event (partial, case-insensitive)")
            @RequestParam(required = false) String actor,
            @Parameter(description = "Filter by category: COUPON_LIFECYCLE or APPROVAL_DECISION")
            @RequestParam(required = false) AuditCategory category,
            @PageableDefault(size = 20) Pageable pageable) {
        AuditFilterRequest filter = new AuditFilterRequest(dateFrom, dateTo, actor, category);
        return ResponseEntity.ok(ApiResponse.success(auditService.getAuditEvents(filter, pageable)));
    }

    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @Operation(summary = "Export the audit feed as an Excel workbook",
            description = "Same filters as GET /audit, newest first, no pagination — the whole filtered feed in one file.")
    public ResponseEntity<StreamingResponseBody> exportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditCategory category) {
        AuditFilterRequest filter = new AuditFilterRequest(dateFrom, dateTo, actor, category);
        StreamingResponseBody body = out -> auditExportService.exportExcel(filter, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"audit-log-%s.xlsx\"".formatted(exportTimestamp()))
                .contentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @Operation(summary = "Export the audit feed as a PDF",
            description = "Same filters as GET /audit, newest first, no pagination — the whole filtered feed in one file.")
    public ResponseEntity<StreamingResponseBody> exportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditCategory category) {
        AuditFilterRequest filter = new AuditFilterRequest(dateFrom, dateTo, actor, category);
        StreamingResponseBody body = out -> auditExportService.exportPdf(filter, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"audit-log-%s.pdf\"".formatted(exportTimestamp()))
                .contentType(MediaType.APPLICATION_PDF)
                .body(body);
    }

    private String exportTimestamp() {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    }
}
