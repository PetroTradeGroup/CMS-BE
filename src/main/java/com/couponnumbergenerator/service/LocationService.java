package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.CreateLocationRequest;
import com.couponnumbergenerator.dto.request.UpdateLocationRequest;
import com.couponnumbergenerator.dto.response.LocationResponse;

import java.util.List;

public interface LocationService {

    LocationResponse create(CreateLocationRequest request);

    List<LocationResponse> getAll(Boolean active);

    LocationResponse getById(Long id);

    LocationResponse update(Long id, UpdateLocationRequest request);
}