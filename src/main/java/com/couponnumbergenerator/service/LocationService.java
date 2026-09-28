package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.CreateLocationRequest;
import com.couponnumbergenerator.dto.request.UpdateLocationRequest;
import com.couponnumbergenerator.dto.response.LocationResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

public interface LocationService {

    LocationResponse create(CreateLocationRequest request);

    PagedResponse<LocationResponse> getAll(Boolean active, Pageable pageable);

    LocationResponse getById(Long id);

    LocationResponse update(Long id, UpdateLocationRequest request);
}