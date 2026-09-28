package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.AuditFilterRequest;
import com.couponnumbergenerator.dto.response.AuditEventResponse;
import com.couponnumbergenerator.repository.AuditRepository;
import com.couponnumbergenerator.service.AuditExportService;
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
public class AuditExportServiceImpl implements AuditExportService {

    private static final String[] COLUMNS = {"Category", "Action", "Actor", "Occurred At",
            "Reference Type", "Reference ID", "Summary"};
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditRepository auditRepository;

    @Override
    @Transactional(readOnly = true)
    public void exportExcel(AuditFilterRequest filter, OutputStream outputStream) throws IOException {
        List<AuditEventResponse> events = loadEvents(filter);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Audit Log");
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
            for (AuditEventResponse event : events) {
                Row row = sheet.createRow(rowIndex++);
                String[] values = rowValues(event);
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
    public void exportPdf(AuditFilterRequest filter, OutputStream outputStream) throws IOException {
        List<AuditEventResponse> events = loadEvents(filter);

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

        document.add(new Paragraph("Audit Log", titleFont));
        Paragraph meta = new Paragraph("%d event(s) — exported %s".formatted(
                events.size(), TIMESTAMP_FORMAT.format(LocalDateTime.now())), metaFont);
        meta.setSpacingAfter(12f);
        document.add(meta);

        PdfPTable table = new PdfPTable(COLUMNS.length);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 1.3f, 1.4f, 1.3f, 1.2f, 0.8f, 3.5f});
        table.setHeaderRows(1);

        for (String column : COLUMNS) {
            PdfPCell cell = new PdfPCell(new Phrase(column, headerFont));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setPadding(5f);
            table.addCell(cell);
        }
        for (AuditEventResponse event : events) {
            for (String value : rowValues(event)) {
                PdfPCell cell = new PdfPCell(new Phrase(value, cellFont));
                cell.setPadding(4f);
                table.addCell(cell);
            }
        }
        document.add(table);
        document.close();
    }

    private List<AuditEventResponse> loadEvents(AuditFilterRequest filter) {
        return auditRepository
                .findAllAuditEvents(filter.categoryName(), filter.trimmedActor(),
                        filter.fromTimestamp(), filter.toTimestampExclusive())
                .stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    private String[] rowValues(AuditEventResponse event) {
        return new String[]{
                event.category(),
                event.action(),
                event.actor() == null ? "" : event.actor(),
                TIMESTAMP_FORMAT.format(event.occurredAt()),
                event.referenceType() == null ? "" : event.referenceType(),
                event.referenceId() == null ? "" : event.referenceId().toString(),
                event.summary() == null ? "" : event.summary()
        };
    }
}
