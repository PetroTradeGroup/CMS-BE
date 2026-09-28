package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.CreateLocationRequest;
import com.couponnumbergenerator.dto.request.UpdateLocationRequest;
import com.couponnumbergenerator.dto.response.LocationResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;

    @Override
    @Transactional
    public LocationResponse create(CreateLocationRequest request) {
        String code = request.code().toUpperCase();
        if (locationRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Location code '%s' already exists".formatted(code));
        }
        if (locationRepository.existsByNameIgnoreCase(request.name())) {
            throw new IllegalArgumentException("Location name '%s' already exists".formatted(request.name()));
        }
        Location saved = locationRepository.save(Location.builder()
                .code(code)
                .name(request.name())
                .type(request.type())
                .address(request.address())
                .build());
        return LocationResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<LocationResponse> getAll(Boolean active, Pageable pageable) {
        Page<Location> page = active == null
                ? locationRepository.findAll(pageable)
                : locationRepository.findByActive(active, pageable);
        return PagedResponse.from(page.map(LocationResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public LocationResponse getById(Long id) {
        return locationRepository.findById(id)
                .map(LocationResponse::from)
                .orElseThrow(() -> new LocationNotFoundException(id));
    }

    @Override
    @Transactional
    public LocationResponse update(Long id, UpdateLocationRequest request) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new LocationNotFoundException(id));
        location.setName(request.name());
        location.setType(request.type());
        location.setAddress(request.address());
        location.setActive(request.active());
        return LocationResponse.from(locationRepository.save(location));
    }
}