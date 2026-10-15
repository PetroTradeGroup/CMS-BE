package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.CompanyRequest;
import com.couponnumbergenerator.dto.request.CreateLocationRequest;
import com.couponnumbergenerator.dto.request.UpdateLocationRequest;
import com.couponnumbergenerator.dto.response.CompanyResponse;
import com.couponnumbergenerator.dto.response.LocationResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.LocationType;
import com.couponnumbergenerator.exception.CompanyNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.Company;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.CompanyRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.LocationService;
import com.couponnumbergenerator.specification.LocationSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;
    private final CompanyRepository companyRepository;

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
        Company company = request.companyId() != null
                ? findCompany(request.companyId())
                : companyRepository.findByCode(Company.DEFAULT_CODE)
                        .orElseThrow(() -> new IllegalStateException("Default company %s is missing".formatted(Company.DEFAULT_CODE)));
        Location saved = locationRepository.save(Location.builder()
                .code(code)
                .name(request.name())
                .type(request.type())
                .address(request.address())
                .company(company)
                .build());
        return LocationResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<LocationResponse> getAll(Boolean active, LocationType type, Long companyId, String q,
                                                  Pageable pageable) {
        Page<Location> page = locationRepository.findAll(
                LocationSpecification.withFilters(active, type, companyId, q), pageable);
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
        Location location = findLocation(id);
        location.setName(request.name());
        location.setType(request.type());
        location.setAddress(request.address());
        location.setActive(request.active());
        if (request.companyId() != null) {
            location.setCompany(findCompany(request.companyId()));
        }
        return LocationResponse.from(locationRepository.save(location));
    }

    @Override
    @Transactional
    public LocationResponse assignCompany(Long locationId, Long companyId) {
        Location location = findLocation(locationId);
        location.setCompany(findCompany(companyId));
        return LocationResponse.from(locationRepository.save(location));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyResponse> getCompanies() {
        return companyRepository.findAll(Sort.by("name")).stream().map(CompanyResponse::from).toList();
    }

    @Override
    @Transactional
    public CompanyResponse createCompany(CompanyRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("Code is required");
        }
        String code = request.code().trim().toUpperCase();
        if (companyRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Company code '%s' already exists".formatted(code));
        }
        if (companyRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new IllegalArgumentException("Company name '%s' already exists".formatted(request.name().trim()));
        }
        return CompanyResponse.from(companyRepository.save(Company.builder()
                .code(code)
                .name(request.name().trim())
                .active(request.active() == null || request.active())
                .build()));
    }

    @Override
    @Transactional
    public CompanyResponse updateCompany(Long id, CompanyRequest request) {
        Company company = findCompany(id);
        String name = request.name().trim();
        if (!company.getName().equalsIgnoreCase(name) && companyRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Company name '%s' already exists".formatted(name));
        }
        company.setName(name);
        if (request.active() != null) {
            company.setActive(request.active());
        }
        return CompanyResponse.from(companyRepository.save(company));
    }

    private Location findLocation(Long id) {
        return locationRepository.findById(id).orElseThrow(() -> new LocationNotFoundException(id));
    }

    private Company findCompany(Long id) {
        return companyRepository.findById(id).orElseThrow(() -> new CompanyNotFoundException(id));
    }
}
