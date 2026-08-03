package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.BatchFilterRequest;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.repository.CouponBatchRepository;
import com.couponnumbergenerator.service.BatchExportService;
import com.couponnumbergenerator.specification.CouponBatchSpecification;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchExportServiceImpl implements BatchExportService {

    private static final String[] COLUMNS = {"Batch Number", "Fuel Type", "Coupon Type", "Coupons",
            "Target Qty (L)", "Origin Location", "Expiry Date", "Created By", "Created At"};
    private static final DateTimeFormatter CREATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CouponBatchRepository couponBatchRepository;

    @Override
    @Transactional(readOnly = true)
    public void exportExcel(BatchFilterRequest filter, OutputStream outputStream) throws IOException {
        List<CouponBatch> batches = loadBatches(filter);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Batches");
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (CouponBatch batch : batches) {
                Row row = sheet.createRow(rowIndex++);
                String[] values = rowValues(batch);
                for (int i = 0; i < values.length; i++) {
                    row.createCell(i).setCellValue(values[i]);
                }
            }
            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(outputStream);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void exportPdf(BatchFilterRequest filter, OutputStream outputStream) throws IOException {
        List<CouponBatch> batches = loadBatches(filter);

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

        document.add(new Paragraph("Coupon Batches", titleFont));
        Paragraph meta = new Paragraph("%d batch(es) — exported %s".formatted(
                batches.size(), CREATED_AT_FORMAT.format(LocalDateTime.now())), metaFont);
        meta.setSpacingAfter(12f);
        document.add(meta);

        PdfPTable table = new PdfPTable(COLUMNS.length);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.4f, 1.2f, 1.1f, 0.9f, 1.2f, 1.4f, 1.2f, 1.2f, 1.5f});
        table.setHeaderRows(1);

        for (String column : COLUMNS) {
            PdfPCell cell = new PdfPCell(new Phrase(column, headerFont));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setPadding(5f);
            table.addCell(cell);
        }
        for (CouponBatch batch : batches) {
            for (String value : rowValues(batch)) {
                PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
                cell.setPadding(4f);
                table.addCell(cell);
            }
        }
        document.add(table);
        document.close();
    }

    private List<CouponBatch> loadBatches(BatchFilterRequest filter) {
        return couponBatchRepository.findAll(CouponBatchSpecification.withFilters(filter),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String[] rowValues(CouponBatch batch) {
        return new String[]{
                batch.getBatchNumber(),
                batch.getFuelType().getName(),
                batch.getCouponType().name(),
                String.valueOf(batch.getQuantity()),
                batch.getTargetQuantity() == null ? "" : batch.getTargetQuantity().toPlainString(),
                batch.getOriginLocation().getName(),
                batch.getExpiryDate() == null ? "" : batch.getExpiryDate().toString(),
                batch.getCreatedBy() == null ? "" : batch.getCreatedBy(),
                CREATED_AT_FORMAT.format(batch.getCreatedAt())
        };
    }
}