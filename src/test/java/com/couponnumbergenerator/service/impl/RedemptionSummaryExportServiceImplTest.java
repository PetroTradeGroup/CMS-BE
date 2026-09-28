package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.DenominationLine;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.FuelTypeSummary;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedemptionSummaryExportServiceImplTest {

    private final RedemptionSummaryExportServiceImpl service = new RedemptionSummaryExportServiceImpl();

    @Test
    void exportExcelWritesDenominationRowsSubtotalAndGrandTotal() throws IOException {
        RedemptionSummaryResponse summary = summary();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportExcel(summary, out);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheet("Redemption Summary");
            Row header = sheet.getRow(3);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Fuel Type");

            Row line1 = sheet.getRow(4);
            assertThat(line1.getCell(0).getStringCellValue()).isEqualTo("Diesel");
            assertThat(line1.getCell(1).getStringCellValue()).isEqualTo("20");
            assertThat(line1.getCell(2).getNumericCellValue()).isEqualTo(3.0);
            assertThat(line1.getCell(3).getStringCellValue()).isEqualTo("60.00");

            Row line2 = sheet.getRow(5);
            assertThat(line2.getCell(1).getStringCellValue()).isEqualTo("50");

            Row subtotal = sheet.getRow(6);
            assertThat(subtotal.getCell(0).getStringCellValue()).isEqualTo("Diesel subtotal");
            assertThat(subtotal.getCell(2).getNumericCellValue()).isEqualTo(4.0);
            assertThat(subtotal.getCell(3).getStringCellValue()).isEqualTo("110.00");

            Row grandTotal = sheet.getRow(7);
            assertThat(grandTotal.getCell(0).getStringCellValue()).isEqualTo("Grand Total");
            assertThat(grandTotal.getCell(2).getNumericCellValue()).isEqualTo(4.0);
            assertThat(grandTotal.getCell(3).getStringCellValue()).isEqualTo("110.00");
        }
    }

    @Test
    void exportPdfProducesPdfDocument() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportPdf(summary(), out);

        byte[] bytes = out.toByteArray();
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(bytes.length).isGreaterThan(300);
    }

    private RedemptionSummaryResponse summary() {
        List<DenominationLine> lines = List.of(
                new DenominationLine(new BigDecimal("20"), 3, new BigDecimal("60.00")),
                new DenominationLine(new BigDecimal("50"), 1, new BigDecimal("50.00")));
        FuelTypeSummary fuelType = new FuelTypeSummary(1L, "Diesel", 4, new BigDecimal("110.00"), lines);
        return new RedemptionSummaryResponse(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), null, null, null,
                4, new BigDecimal("110.00"), List.of(fuelType));
    }
}
