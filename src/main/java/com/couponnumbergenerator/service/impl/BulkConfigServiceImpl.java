package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.UpdateBulkLimitRequest;
import com.couponnumbergenerator.dto.request.UpdatePageSizeLimitRequest;
import com.couponnumbergenerator.dto.request.UpdateValidityPeriodRequest;
import com.couponnumbergenerator.dto.response.BulkLimitResponse;
import com.couponnumbergenerator.dto.response.PageSizeLimitResponse;
import com.couponnumbergenerator.dto.response.ValidityPeriodResponse;
import com.couponnumbergenerator.model.BulkGenerationConfig;
import com.couponnumbergenerator.repository.BulkGenerationConfigRepository;
import com.couponnumbergenerator.service.BulkConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BulkConfigServiceImpl implements BulkConfigService {

    public static final long CONFIG_ID = 1L;

    private final BulkGenerationConfigRepository bulkGenerationConfigRepository;

    @Override
    @Transactional(readOnly = true)
    public BulkLimitResponse get() {
        return toResponse(loadConfig());
    }

    @Override
    @Transactional
    public BulkLimitResponse update(UpdateBulkLimitRequest request) {
        BulkGenerationConfig config = loadConfig();
        config.setMaxCount(request.maxCount());
        return toResponse(bulkGenerationConfigRepository.save(config));
    }

    @Override
    @Transactional(readOnly = true)
    public int getMaxCount() {
        return loadConfig().getMaxCount();
    }

    private BulkGenerationConfig loadConfig() {
        return bulkGenerationConfigRepository.findById(CONFIG_ID)
                .orElseThrow(() -> new IllegalStateException("Bulk generation config not initialised"));
    }

    @Override
    @Transactional(readOnly = true)
    public PageSizeLimitResponse getPageSizeLimit() {
        return toPageSizeResponse(loadConfig());
    }

    @Override
    @Transactional
    public PageSizeLimitResponse updatePageSizeLimit(UpdatePageSizeLimitRequest request) {
        BulkGenerationConfig config = loadConfig();
        config.setMaxPageSize(request.maxPageSize());
        return toPageSizeResponse(bulkGenerationConfigRepository.save(config));
    }

    @Override
    @Transactional(readOnly = true)
    public int getMaxPageSize() {
        return loadConfig().getMaxPageSize();
    }

    @Override
    @Transactional(readOnly = true)
    public ValidityPeriodResponse getValidityPeriod() {
        return toValidityResponse(loadConfig());
    }

    @Override
    @Transactional
    public ValidityPeriodResponse updateValidityPeriod(UpdateValidityPeriodRequest request) {
        BulkGenerationConfig config = loadConfig();
        config.setDefaultValidityDays(request.defaultValidityDays());
        return toValidityResponse(bulkGenerationConfigRepository.save(config));
    }

    @Override
    @Transactional(readOnly = true)
    public int getDefaultValidityDays() {
        return loadConfig().getDefaultValidityDays();
    }

    private ValidityPeriodResponse toValidityResponse(BulkGenerationConfig config) {
        return new ValidityPeriodResponse(config.getDefaultValidityDays(), config.getUpdatedAt());
    }

    private BulkLimitResponse toResponse(BulkGenerationConfig config) {
        return new BulkLimitResponse(config.getMaxCount(), config.getUpdatedAt());
    }

    private PageSizeLimitResponse toPageSizeResponse(BulkGenerationConfig config) {
        return new PageSizeLimitResponse(config.getMaxPageSize(), config.getUpdatedAt());
    }
}