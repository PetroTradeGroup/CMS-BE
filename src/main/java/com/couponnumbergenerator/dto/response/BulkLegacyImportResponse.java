package com.couponnumbergenerator.dto.response;

import java.util.List;

/**
 * Result of one legacy-coupon spreadsheet upload: every row is validated independently, so one
 * bad row (duplicate, unknown fuel type code, ...) never blocks the rest of the file. In
 * {@code dryRun} mode nothing is persisted — {@link LegacyImportRowResult#success()} reports
 * what each row would have done.
 */
public record BulkLegacyImportResponse(
        int totalRows,
        int succeeded,
        int failed,
        boolean dryRun,
        List<LegacyImportRowResult> results
) {
    public static BulkLegacyImportResponse of(boolean dryRun, List<LegacyImportRowResult> results) {
        long succeeded = results.stream().filter(LegacyImportRowResult::success).count();
        return new BulkLegacyImportResponse(results.size(), (int) succeeded,
                results.size() - (int) succeeded, dryRun, results);
    }
}
