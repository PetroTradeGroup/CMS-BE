package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.DenominationLine;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse.FuelTypeSummary;
import com.couponnumbergenerator.service.RedemptionSummaryExportService;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class RedemptionSummaryExportServiceImpl implements RedemptionSummaryExportService {

    private static final String[] COLUMNS = {"Fuel Type", "Denomination", "Coupons", "Litres"};
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public void exportExcel(RedemptionSummaryResponse summary, OutputStream outputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Redemption Summary");
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle boldStyle = workbook.createCellStyle();
            boldStyle.setFont(headerFont);

            int rowIndex = 0;
            rowIndex = writeMetaRow(sheet, rowIndex, "Redemption Summary: %s to %s"
                    .formatted(summary.dateFrom(), summary.dateTo()));
            rowIndex = writeMetaRow(sheet, rowIndex, metaLine(summary));
            rowIndex++;

            Row header = sheet.createRow(rowIndex++);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNS[i]);
                cell.setCellStyle(headerStyle);
            }

            for (FuelTypeSummary fuelType : summary.byFuelType()) {
                for (DenominationLine line : fuelType.byDenomination()) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(fuelType.fuelTypeName());
                    row.createCell(1).setCellValue(line.denomination().toPlainString());
                    row.createCell(2).setCellValue(line.count());
                    row.createCell(3).setCellValue(line.litres().toPlainString());
                }
                Row subtotal = sheet.createRow(rowIndex++);
                writeBoldRow(subtotal, boldStyle, fuelType.fuelTypeName() + " subtotal", "",
                        fuelType.count(), fuelType.litres());
            }
            Row grandTotal = sheet.createRow(rowIndex);
            writeBoldRow(grandTotal, boldStyle, "Grand Total", "", summary.totalCoupons(), summary.totalLitres());

            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
        }
    }

    /** Attendant-narrowed report leads with the attendant, not the site — a Team Leader already knows which site this is. */
    private String metaLine(RedemptionSummaryResponse summary) {
        String scope = summary.attendantUsername() != null
                ? "Attendant: " + summary.attendantUsername()
                : "Location: " + (summary.locationId() == null ? "All sites" : "Site #" + summary.locationId());
        return "%s   Fuel type filter: %s   Generated: %s".formatted(scope,
                summary.fuelTypeId() == null ? "All" : "#" + summary.fuelTypeId(),
                TIMESTAMP_FORMAT.format(LocalDateTime.now()));
    }

    private int writeMetaRow(Sheet sheet, int rowIndex, String text) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(text);
        return rowIndex + 1;
    }

    private void writeBoldRow(Row row, CellStyle boldStyle, String label, String denomination, long count, java.math.BigDecimal litres) {
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(boldStyle);
        Cell denomCell = row.createCell(1);
        denomCell.setCellValue(denomination);
        denomCell.setCellStyle(boldStyle);
        Cell countCell = row.createCell(2);
        countCell.setCellValue(count);
        countCell.setCellStyle(boldStyle);
        Cell litresCell = row.createCell(3);
        litresCell.setCellValue(litres.toPlainString());
        litresCell.setCellStyle(boldStyle);
    }

    @Override
    public void exportPdf(RedemptionSummaryResponse summary, OutputStream outputStream) throws IOException {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        com.lowagie.text.Font titleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 14,
                com.lowagie.text.Font.BOLD);
        com.lowagie.text.Font metaFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9,
                com.lowagie.text.Font.NORMAL, Color.DARK_GRAY);
        com.lowagie.text.Font headerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9,
                com.lowagie.text.Font.BOLD, Color.WHITE);
        com.lowagie.text.Font cellFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9,
                com.lowagie.text.Font.NORMAL);
        com.lowagie.text.Font boldCellFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9,
                com.lowagie.text.Font.BOLD);

        document.add(new Paragraph("Redemption Summary: %s to %s".formatted(summary.dateFrom(), summary.dateTo()), titleFont));
        Paragraph meta = new Paragraph(metaLine(summary), metaFont);
        meta.setSpacingAfter(12f);
        document.add(meta);

        PdfPTable table = new PdfPTable(COLUMNS.length);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.2f, 1.4f, 1.2f, 1.4f});
        table.setHeaderRows(1);

        for (String column : COLUMNS) {
            PdfPCell cell = new PdfPCell(new Phrase(column, headerFont));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setPadding(5f);
            table.addCell(cell);
        }
        for (FuelTypeSummary fuelType : summary.byFuelType()) {
            for (DenominationLine line : fuelType.byDenomination()) {
                addCell(table, fuelType.fuelTypeName(), cellFont);
                addCell(table, line.denomination().toPlainString(), cellFont);
                addCell(table, String.valueOf(line.count()), cellFont);
                addCell(table, line.litres().toPlainString(), cellFont);
            }
            addCell(table, fuelType.fuelTypeName() + " subtotal", boldCellFont);
            addCell(table, "", boldCellFont);
            addCell(table, String.valueOf(fuelType.count()), boldCellFont);
            addCell(table, fuelType.litres().toPlainString(), boldCellFont);
        }
        addCell(table, "Grand Total", boldCellFont);
        addCell(table, "", boldCellFont);
        addCell(table, String.valueOf(summary.totalCoupons()), boldCellFont);
        addCell(table, summary.totalLitres().toPlainString(), boldCellFont);

        document.add(table);
        document.close();
    }

    private void addCell(PdfPTable table, String value, com.lowagie.text.Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(4f);
        table.addCell(cell);
    }
}
