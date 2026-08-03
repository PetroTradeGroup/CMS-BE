package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.CreateDepartmentRequest;
import com.couponnumbergenerator.dto.request.UpdateDepartmentRequest;
import com.couponnumbergenerator.dto.response.DepartmentResponse;
import com.couponnumbergenerator.exception.DepartmentNotFoundException;
import com.couponnumbergenerator.model.Department;
import com.couponnumbergenerator.repository.DepartmentRepository;
import com.couponnumbergenerator.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Override
    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        String code = request.code().toUpperCase();
        if (departmentRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Department code '%s' already exists".formatted(code));
        }
        if (departmentRepository.existsByNameIgnoreCase(request.name())) {
            throw new IllegalArgumentException("Department name '%s' already exists".formatted(request.name()));
        }
        Department saved = departmentRepository.save(Department.builder()
                .code(code)
                .name(request.name())
                .build());
        return DepartmentResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAll(Boolean active) {
        List<Department> departments = active == null
                ? departmentRepository.findAll(Sort.by("name"))
                : departmentRepository.findByActive(active);
        return departments.stream().map(DepartmentResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentResponse getById(Long id) {
        return departmentRepository.findById(id)
                .map(DepartmentResponse::from)
                .orElseThrow(() -> new DepartmentNotFoundException(id));
    }

    @Override
    @Transactional
    public DepartmentResponse update(Long id, UpdateDepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new DepartmentNotFoundException(id));
        department.setName(request.name());
        department.setActive(request.active());
        return DepartmentResponse.from(departmentRepository.save(department));
    }
}
