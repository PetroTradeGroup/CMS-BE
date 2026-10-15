package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.model.Company;

public record CompanyResponse(Long id, String code, String name, boolean active) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(company.getId(), company.getCode(), company.getName(), company.isActive());
    }
}
