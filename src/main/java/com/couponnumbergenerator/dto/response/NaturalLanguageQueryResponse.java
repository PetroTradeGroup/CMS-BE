package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.dto.request.CouponFilterRequest;

public record NaturalLanguageQueryResponse(
        String question,
        String interpretation,
        CouponFilterRequest appliedFilters,
        PagedResponse<CouponResponse> results
) {}