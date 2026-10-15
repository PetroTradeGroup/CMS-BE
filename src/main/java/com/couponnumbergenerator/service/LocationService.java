package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.CompanyRequest;
import com.couponnumbergenerator.dto.request.CreateLocationRequest;
import com.couponnumbergenerator.dto.request.UpdateLocationRequest;
import com.couponnumbergenerator.dto.response.CompanyResponse;
import com.couponnumbergenerator.dto.response.LocationResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.LocationType;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LocationService {

    LocationResponse create(CreateLocationRequest request);

    PagedResponse<LocationResponse> getAll(Boolean active, LocationType type, Long companyId, String q,
                                           Pageable pageable);

    LocationResponse getById(Long id);

    LocationResponse update(Long id, UpdateLocationRequest request);

    /** Moves a site under another company — what its redemptions are reported under from then on. */
    LocationResponse assignCompany(Long locationId, Long companyId);

    List<CompanyResponse> getCompanies();

    CompanyResponse createCompany(CompanyRequest request);

    CompanyResponse updateCompany(Long id, CompanyRequest request);
}
