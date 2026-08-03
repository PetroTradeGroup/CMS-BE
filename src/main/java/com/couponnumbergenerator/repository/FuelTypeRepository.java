package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.model.FuelType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FuelTypeRepository extends JpaRepository<FuelType, Long> {

    Optional<FuelType> findByName(String name);

    Page<FuelType> findByActiveTrue(Pageable pageable);

    boolean existsByName(String name);

    boolean existsByTypeCode(String typeCode);
}