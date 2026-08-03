package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.ai.ExtractedFilter;
import com.couponnumbergenerator.ai.QueryFilterExtractor;
import com.couponnumbergenerator.dto.request.CouponFilterRequest;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.dto.response.FuelTypeResponse;
import com.couponnumbergenerator.dto.response.NaturalLanguageQueryResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.service.CouponService;
import com.couponnumbergenerator.service.NaturalLanguageQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaturalLanguageQueryServiceImpl implements NaturalLanguageQueryService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final QueryFilterExtractor queryFilterExtractor;
    private final CouponService couponService;
    private final FuelTypeRepository fuelTypeRepository;

    @Override
    public NaturalLanguageQueryResponse query(String question, int page, int size) {
        log.info("Processing natural language query: '{}' (page={}, size={})", question, page, size);

        List<FuelTypeResponse> fuelTypes = fuelTypeRepository.findAll()
                .stream()
                .map(this::toFuelTypeResponse)
                .toList();

        ExtractedFilter filter = queryFilterExtractor.extract(question, fuelTypes);

        CouponFilterRequest couponFilter = new CouponFilterRequest(
                filter.dateFrom(),
                filter.dateTo(),
                filter.fuelTypeId(),
                filter.status()
        );

        Pageable pageable = PageRequest.of(page, size, DEFAULT_SORT);
        PagedResponse<CouponResponse> results = couponService.getCoupons(couponFilter, pageable);

        log.info("Query returned {} result(s)", results.totalElements());
        return new NaturalLanguageQueryResponse(question, filter.interpretation(), couponFilter, results);
    }

    private FuelTypeResponse toFuelTypeResponse(FuelType ft) {
        return new FuelTypeResponse(ft.getId(), ft.getName(), ft.getTypeCode(), ft.getDescription(), ft.isActive());
    }
}