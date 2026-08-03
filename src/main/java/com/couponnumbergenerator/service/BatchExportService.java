package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.BatchFilterRequest;

import java.io.IOException;
import java.io.OutputStream;

public interface BatchExportService {

    /** Streams the filtered batch list as an .xlsx workbook (one row per batch, newest first). */
    void exportExcel(BatchFilterRequest filter, OutputStream outputStream) throws IOException;

    /** Streams the filtered batch list as a landscape-A4 PDF table (one row per batch, newest first). */
    void exportPdf(BatchFilterRequest filter, OutputStream outputStream) throws IOException;
}