package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.CreateFuelTypeRequest;
import com.couponnumbergenerator.dto.response.FuelTypeResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.service.FuelTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FuelTypeServiceImpl implements FuelTypeService {

    private final FuelTypeRepository fuelTypeRepository;

    @Override
    @Transactional
    public FuelTypeResponse create(CreateFuelTypeRequest request) {
        String name = request.name().toUpperCase();
        if (fuelTypeRepository.existsByName(name)) {
            throw new IllegalArgumentException("Fuel type '%s' already exists".formatted(name));
        }
        if (fuelTypeRepository.existsByTypeCode(request.typeCode())) {
            throw new IllegalArgumentException("Type code '%s' is already in use".formatted(request.typeCode()));
        }
        FuelType saved = fuelTypeRepository.save(FuelType.builder()
                .name(name)
                .typeCode(request.typeCode())
                .description(request.description())
                .pricePerLitre(request.pricePerLitre())
                .build());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<FuelTypeResponse> getAll(Pageable pageable) {
        return PagedResponse.from(fuelTypeRepository.findAll(pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<FuelTypeResponse> getAllActive(Pageable pageable) {
        return PagedResponse.from(fuelTypeRepository.findByActiveTrue(pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public FuelTypeResponse getById(Long id) {
        return fuelTypeRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new FuelTypeNotFoundException(id));
    }

    @Override
    @Transactional
    public FuelTypeResponse update(Long id, CreateFuelTypeRequest request) {
        FuelType fuelType = fuelTypeRepository.findById(id)
                .orElseThrow(() -> new FuelTypeNotFoundException(id));
        fuelType.setName(request.name().toUpperCase());
        fuelType.setTypeCode(request.typeCode());
        fuelType.setDescription(request.description());
        fuelType.setPricePerLitre(request.pricePerLitre());
        return toResponse(fuelTypeRepository.save(fuelType));
    }

    @Override
    @Transactional
    public void activate(Long id) {
        FuelType fuelType = fuelTypeRepository.findById(id)
                .orElseThrow(() -> new FuelTypeNotFoundException(id));
        fuelType.setActive(true);
        fuelTypeRepository.save(fuelType);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        FuelType fuelType = fuelTypeRepository.findById(id)
                .orElseThrow(() -> new FuelTypeNotFoundException(id));
        fuelType.setActive(false);
        fuelTypeRepository.save(fuelType);
    }

    public FuelTypeResponse toResponse(FuelType fuelType) {
        return FuelTypeResponse.from(fuelType);
    }
}