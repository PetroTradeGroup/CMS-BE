package com.couponnumbergenerator.repository;

import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.enums.LocationType;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.projection.InventorySummaryRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventorySummaryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private CouponRepository couponRepository;
    @Autowired private FuelTypeRepository fuelTypeRepository;
    @Autowired private LocationRepository locationRepository;

    private Location hq;
    private Location depot;
    private FuelType petrol;
    private FuelType diesel;

    @BeforeEach
    void seed() {
        couponRepository.deleteAll();
        // HQ is seeded by the V2 migration.
        hq = locationRepository.findByCode("HQ").orElseThrow();
        depot = locationRepository.findByCode("DEP1").orElseGet(() -> locationRepository.save(
                Location.builder().code("DEP1").name("North Depot").type(LocationType.DEPOT).build()));
        petrol = fuelTypeRepository.findAll().stream()
                .filter(ft -> ft.getName().equals("PETROL")).findFirst()
                .orElseGet(() -> fuelTypeRepository.save(
                        FuelType.builder().name("PETROL").typeCode("002").build()));
        diesel = fuelTypeRepository.findAll().stream()
                .filter(ft -> ft.getName().equals("DIESEL")).findFirst()
                .orElseGet(() -> fuelTypeRepository.save(
                        FuelType.builder().name("DIESEL").typeCode("006").build()));

        saveCoupon("PU002M0000001", petrol, hq, CouponStatus.IN_STOCK);
        saveCoupon("PU002M0000002", petrol, hq, CouponStatus.IN_STOCK);
        saveCoupon("PU002M0000003", petrol, depot, CouponStatus.ALLOCATED);
        saveCoupon("PU006M0000001", diesel, hq, CouponStatus.IN_STOCK);
    }

    private void saveCoupon(String number, FuelType fuelType, Location location, CouponStatus status) {
        couponRepository.save(Coupon.builder()
                .couponNumber(number)
                .fuelType(fuelType)
                .currentLocation(location)
                .couponType(CouponType.PHYSICAL)
                .status(status)
                .build());
    }

    @Test
    void summarizesAllStockWhenNoFilterIsGiven() {
        List<InventorySummaryRow> rows = couponRepository.summarizeInventory(null, null, null);

        assertThat(rows).hasSize(3);
        assertThat(rows.stream().mapToLong(InventorySummaryRow::getCount).sum()).isEqualTo(4);
    }

    @Test
    void filtersByLocation() {
        List<InventorySummaryRow> rows = couponRepository.summarizeInventory(depot.getId(), null, null);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getLocationCode()).isEqualTo("DEP1");
        assertThat(rows.getFirst().getStatus()).isEqualTo(CouponStatus.ALLOCATED);
        assertThat(rows.getFirst().getCount()).isEqualTo(1);
    }

    @Test
    void filtersByFuelType() {
        List<InventorySummaryRow> rows = couponRepository.summarizeInventory(null, diesel.getId(), null);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getFuelTypeName()).isEqualTo("DIESEL");
        assertThat(rows.getFirst().getCount()).isEqualTo(1);
    }

    @Test
    void filtersByStatus() {
        List<InventorySummaryRow> rows = couponRepository.summarizeInventory(null, null, CouponStatus.IN_STOCK);

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(CouponStatus.IN_STOCK));
        assertThat(rows.stream().mapToLong(InventorySummaryRow::getCount).sum()).isEqualTo(3);
    }

    @Test
    void combinesAllFilters() {
        List<InventorySummaryRow> rows = couponRepository.summarizeInventory(
                hq.getId(), petrol.getId(), CouponStatus.IN_STOCK);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getCount()).isEqualTo(2);
    }
}