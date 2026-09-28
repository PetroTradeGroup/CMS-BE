package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.dto.response.BulkLegacyImportResponse;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.Department;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.DepartmentRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.BulkConfigService;
import com.couponnumbergenerator.service.CouponLifecycleService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplLegacyBulkImportTest {

    @Mock private CouponRepository couponRepository;
    @Mock private FuelTypeRepository fuelTypeRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private BulkConfigService bulkConfigService;
    @Mock private CouponLifecycleService couponLifecycleService;

    private CouponServiceImpl service;

    private FuelType petrol;
    private Location hq;
    private Department stocks;

    private static final String[] HEADERS =
            {"couponNumber", "fuelTypeCode", "denomination", "locationCode", "departmentCode", "expiryDate"};

    @BeforeEach
    void setUp() {
        service = new CouponServiceImpl(couponRepository, null, fuelTypeRepository, locationRepository,
                departmentRepository, null, bulkConfigService, couponLifecycleService);

        petrol = FuelType.builder().id(1L).name("Petrol").typeCode("PU").active(true).build();
        hq = Location.builder().id(1L).code("HQ").name("Head Office").build();
        stocks = Department.builder().id(1L).code("STOCKS").name("Stocks").build();

        lenient().when(fuelTypeRepository.findByTypeCode("PU")).thenReturn(Optional.of(petrol));
        lenient().when(locationRepository.findByCode(CouponConstants.DEFAULT_LOCATION_CODE)).thenReturn(Optional.of(hq));
        lenient().when(departmentRepository.findByCode(CouponConstants.DEFAULT_DEPARTMENT_CODE)).thenReturn(Optional.of(stocks));
        lenient().when(couponRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void importsEveryValidRowAndSkipsBlankTrailingRows() throws IOException {
        MockMultipartFile file = workbook(
                new String[]{"LEG-001", "PU", "20.00", "", "", ""},
                new String[]{"LEG-002", "PU", "50.00", "", "", "2027-01-01"},
                new String[]{"", "", "", "", "", ""} // trailing blank row
        );
        when(couponRepository.existsByCouponNumber(any())).thenReturn(false);

        BulkLegacyImportResponse response = service.importLegacyCoupons(file, false, "clerk1");

        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.succeeded()).isEqualTo(2);
        assertThat(response.failed()).isZero();
        assertThat(response.results()).allMatch(r -> r.success());

        verify(couponRepository).saveAll(anyList());
        verify(couponLifecycleService).recordGeneration(anyList(), org.mockito.ArgumentMatchers.eq("clerk1"));
    }

    @Test
    void dryRunValidatesWithoutPersisting() throws IOException {
        MockMultipartFile file = workbook(new String[]{"LEG-010", "PU", "20.00", "", "", ""});
        when(couponRepository.existsByCouponNumber(any())).thenReturn(false);

        BulkLegacyImportResponse response = service.importLegacyCoupons(file, true, "clerk1");

        assertThat(response.dryRun()).isTrue();
        assertThat(response.succeeded()).isEqualTo(1);
        verify(couponRepository, never()).saveAll(anyList());
        verify(couponLifecycleService, never()).recordGeneration(anyList(), any());
    }

    @Test
    void perRowFailureDoesNotBlockTheRestOfTheFile() throws IOException {
        MockMultipartFile file = workbook(
                new String[]{"LEG-DUP", "PU", "20.00", "", "", ""},
                new String[]{"LEG-OK", "PU", "20.00", "", "", ""},
                new String[]{"LEG-BADFUEL", "XX", "20.00", "", "", ""}
        );
        when(couponRepository.existsByCouponNumber("LEG-DUP")).thenReturn(true);
        when(couponRepository.existsByCouponNumber("LEG-OK")).thenReturn(false);
        when(couponRepository.existsByCouponNumber("LEG-BADFUEL")).thenReturn(false);
        when(fuelTypeRepository.findByTypeCode("XX")).thenReturn(Optional.empty());

        BulkLegacyImportResponse response = service.importLegacyCoupons(file, false, "clerk1");

        assertThat(response.totalRows()).isEqualTo(3);
        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(2);
        assertThat(response.results().get(0).success()).isFalse();
        assertThat(response.results().get(0).reason()).isEqualTo("Coupon number already exists");
        assertThat(response.results().get(1).success()).isTrue();
        assertThat(response.results().get(2).success()).isFalse();
        assertThat(response.results().get(2).reason()).contains("Unknown fuel type code");

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(couponRepository).saveAll(captor.capture());
        List<Coupon> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getCouponNumber()).isEqualTo("LEG-OK");
    }

    private MockMultipartFile workbook(String[]... rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Legacy");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int r = 1;
            for (String[] row : rows) {
                Row excelRow = sheet.createRow(r++);
                for (int c = 0; c < row.length; c++) {
                    if (row[c] != null && !row[c].isEmpty()) {
                        excelRow.createCell(c).setCellValue(row[c]);
                    }
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "legacy.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
