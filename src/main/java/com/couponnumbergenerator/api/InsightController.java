package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.InsightResponse;
import com.couponnumbergenerator.service.InsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.INSIGHTS_PATH)
@Tag(name = "AI Insights", description = "AI-generated business intelligence on coupon usage trends")
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class InsightController {

    private final InsightService insightService;

    @GetMapping
    @Operation(
            summary = "Generate AI business insights",
            description = "Collects live coupon statistics and asks Ollama to produce a management-level narrative. " +
                          "Requires Ollama to be running locally (ollama serve)."
    )
    public ResponseEntity<ApiResponse<InsightResponse>> getInsights() {
        return ResponseEntity.ok(
                ApiResponse.success("Insights generated successfully", insightService.generateInsights())
        );
    }
}