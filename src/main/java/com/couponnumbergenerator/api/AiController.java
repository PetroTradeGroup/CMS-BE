package com.couponnumbergenerator.api;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.request.NaturalLanguageQueryRequest;
import com.couponnumbergenerator.dto.response.ApiResponse;
import com.couponnumbergenerator.dto.response.NaturalLanguageQueryResponse;
import com.couponnumbergenerator.service.NaturalLanguageQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(CouponConstants.API_BASE_PATH + CouponConstants.AI_PATH)
@Tag(name = "AI", description = "Natural language coupon queries powered by Ollama")
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class AiController {

    private final NaturalLanguageQueryService naturalLanguageQueryService;

    @PostMapping("/query")
    @Operation(
            summary = "Query coupons in plain English",
            description = """
                    Send a natural language question and receive matching coupons.
                    Examples:
                    - "Show me all expired diesel coupons from last week"
                    - "How many active petrol coupons were generated this month?"
                    - "Find used coupons from January 2026"
                    Requires Ollama to be running locally (ollama serve).
                    """
    )
    public ResponseEntity<ApiResponse<NaturalLanguageQueryResponse>> query(
            @Valid @RequestBody NaturalLanguageQueryRequest request,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Results per page (1–100)") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(
                ApiResponse.success(naturalLanguageQueryService.query(request.question(), page, size))
        );
    }
}