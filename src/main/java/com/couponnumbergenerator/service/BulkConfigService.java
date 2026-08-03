package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.UpdateBulkLimitRequest;
import com.couponnumbergenerator.dto.request.UpdatePageSizeLimitRequest;
import com.couponnumbergenerator.dto.request.UpdateValidityPeriodRequest;
import com.couponnumbergenerator.dto.response.BulkLimitResponse;
import com.couponnumbergenerator.dto.response.PageSizeLimitResponse;
import com.couponnumbergenerator.dto.response.ValidityPeriodResponse;

public interface BulkConfigService {

    BulkLimitResponse get();

    BulkLimitResponse update(UpdateBulkLimitRequest request);

    int getMaxCount();

    PageSizeLimitResponse getPageSizeLimit();

    PageSizeLimitResponse updatePageSizeLimit(UpdatePageSizeLimitRequest request);

    int getMaxPageSize();

    ValidityPeriodResponse getValidityPeriod();

    ValidityPeriodResponse updateValidityPeriod(UpdateValidityPeriodRequest request);

    /** Days a coupon stays valid after generation when the generate request has no explicit expiryDate. */
    int getDefaultValidityDays();
}