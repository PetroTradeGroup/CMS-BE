package com.couponnumbergenerator.dto.response;

/** The outcome of one row of an uploaded legacy-coupon spreadsheet. */
public record LegacyImportRowResult(
        int row,
        String couponNumber,
        boolean success,
        /** Null when {@code success}; otherwise why the row was rejected. */
        String reason
) {
    public static LegacyImportRowResult ok(int row, String couponNumber) {
        return new LegacyImportRowResult(row, couponNumber, true, null);
    }

    public static LegacyImportRowResult failed(int row, String couponNumber, String reason) {
        return new LegacyImportRowResult(row, couponNumber, false, reason);
    }
}
