package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.CreateFuelTypeRequest;
import com.couponnumbergenerator.dto.response.FuelTypeResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

public interface FuelTypeService {

    FuelTypeResponse create(CreateFuelTypeRequest request);

    PagedResponse<FuelTypeResponse> getAll(Pageable pageable);

    PagedResponse<FuelTypeResponse> getAllActive(Pageable pageable);

    FuelTypeResponse getById(Long id);

    FuelTypeResponse update(Long id, CreateFuelTypeRequest request);

    void activate(Long id);

    void deactivate(Long id);
}