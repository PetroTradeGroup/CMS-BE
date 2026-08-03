package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.CreateDepartmentRequest;
import com.couponnumbergenerator.dto.request.UpdateDepartmentRequest;
import com.couponnumbergenerator.dto.response.DepartmentResponse;

import java.util.List;

public interface DepartmentService {

    DepartmentResponse create(CreateDepartmentRequest request);

    List<DepartmentResponse> getAll(Boolean active);

    DepartmentResponse getById(Long id);

    DepartmentResponse update(Long id, UpdateDepartmentRequest request);
}
