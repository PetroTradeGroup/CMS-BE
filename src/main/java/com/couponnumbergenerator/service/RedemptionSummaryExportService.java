package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.response.CouponActivityResponse;
import com.couponnumbergenerator.dto.response.RedemptionLogEntry;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public interface RedemptionSummaryExportService {

    /** Streams a redemption summary as an .xlsx workbook — one row per fuel type/denomination, with fuel-type subtotals and a grand total. */
    void exportExcel(RedemptionSummaryResponse summary, OutputStream outputStream) throws IOException;

    /** Streams a redemption summary as a landscape-A4 PDF table — same rows as exportExcel. */
    void exportPdf(RedemptionSummaryResponse summary, OutputStream outputStream) throws IOException;

    /** Streams a coupon activity or balance report as .xlsx (title is the first line) — one row per status/fuel type/denomination, with fuel-type and status subtotals. */
    void exportActivityExcel(CouponActivityResponse activity, String title, OutputStream outputStream) throws IOException;

    /** Streams the redemption log as .xlsx — one row per redemption with its timestamp, plus a total row. */
    void exportLogExcel(List<RedemptionLogEntry> entries, String title, OutputStream outputStream) throws IOException;
}
