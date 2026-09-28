package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.BatchFilterRequest;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.CouponBatchRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchExportServiceImplTest {

    @Mock private CouponBatchRepository couponBatchRepository;

    @InjectMocks
    private BatchExportServiceImpl service;

    @Test
    void exportExcelWritesHeaderAndOneRowPerBatch() throws IOException {
        when(couponBatchRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(batch("BAT-001"), batch("BAT-002")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportExcel(new BatchFilterRequest(null, null, null, null, null, null), out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheet("Batches");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Batch Number");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("BAT-001");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Diesel");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("100.00");
            assertThat(sheet.getRow(0).getCell(9).getStringCellValue()).isEqualTo("Batch Seq");
            assertThat(sheet.getRow(1).getCell(9).getStringCellValue()).isEqualTo("7");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("BAT-002");
        }
    }

    @Test
    void exportPdfProducesPdfDocument() throws IOException {
        when(couponBatchRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(batch("BAT-001")));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportPdf(new BatchFilterRequest(null, null, null, null, null, null), out);

        byte[] bytes = out.toByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(bytes.length).isGreaterThan(500);
    }

    private CouponBatch batch(String batchNumber) {
        return CouponBatch.builder()
                .batchNumber(batchNumber)
                .sequenceNumber(7L)
                .fuelType(FuelType.builder().name("Diesel").build())
                .couponType(CouponType.PHYSICAL)
                .quantity(5)
                .targetQuantity(new BigDecimal("100.00"))
                .originLocation(Location.builder().name("Head Office").build())
                .expiryDate(LocalDate.of(2027, 7, 17))
                .createdBy("tadie")
                .createdAt(LocalDateTime.of(2026, 7, 17, 12, 0))
                .build();
    }
}