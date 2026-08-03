package com.couponnumbergenerator.config;

import com.couponnumbergenerator.model.BulkGenerationConfig;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.repository.BulkGenerationConfigRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.service.impl.BulkConfigServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final int DEFAULT_BULK_MAX = 20_000;
    private static final int DEFAULT_PAGE_SIZE_MAX = 500;
    private static final int DEFAULT_VALIDITY_DAYS = 365;

    private final FuelTypeRepository fuelTypeRepository;
    private final BulkGenerationConfigRepository bulkGenerationConfigRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedFuelType("PETROL", "002", "Petrol fuel");
        seedFuelType("DIESEL", "006", "Diesel fuel");
        seedBulkConfig();
    }

    private void seedFuelType(String name, String typeCode, String description) {
        if (!fuelTypeRepository.existsByName(name)) {
            fuelTypeRepository.save(FuelType.builder()
                    .name(name)
                    .typeCode(typeCode)
                    .description(description)
                    .build());
            log.info("Seeded fuel type: {} ({})", name, typeCode);
        }
    }

    private void seedBulkConfig() {
        if (!bulkGenerationConfigRepository.existsById(BulkConfigServiceImpl.CONFIG_ID)) {
            bulkGenerationConfigRepository.save(BulkGenerationConfig.builder()
                    .id(BulkConfigServiceImpl.CONFIG_ID)
                    .maxCount(DEFAULT_BULK_MAX)
                    .maxPageSize(DEFAULT_PAGE_SIZE_MAX)
                    .defaultValidityDays(DEFAULT_VALIDITY_DAYS)
                    .build());
            log.info("Seeded bulk generation config: maxCount={}, maxPageSize={}, defaultValidityDays={}",
                    DEFAULT_BULK_MAX, DEFAULT_PAGE_SIZE_MAX, DEFAULT_VALIDITY_DAYS);
        }
    }
}