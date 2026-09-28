package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.AuditFilterRequest;

import java.io.IOException;
import java.io.OutputStream;

public interface AuditExportService {

    /** Streams the filtered audit feed as an .xlsx workbook (one row per event, newest first). */
    void exportExcel(AuditFilterRequest filter, OutputStream outputStream) throws IOException;

    /** Streams the filtered audit feed as a landscape-A4 PDF table (one row per event, newest first). */
    void exportPdf(AuditFilterRequest filter, OutputStream outputStream) throws IOException;
}
