package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.model.BulkGenerationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkGenerationConfigRepository extends JpaRepository<BulkGenerationConfig, Long> {
}