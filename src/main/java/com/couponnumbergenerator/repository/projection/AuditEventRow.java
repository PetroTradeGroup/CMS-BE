package com.couponnumbergenerator.repository.projection;

import java.time.LocalDateTime;

/** One row of the merged system-wide audit feed — see {@link com.couponnumbergenerator.repository.AuditRepository}. */
public interface AuditEventRow {

    String getCategory();

    String getAction();

    String getActor();

    LocalDateTime getOccurredAt();

    String getReferenceType();

    Long getReferenceId();

    /** Human-readable one-liner, e.g. "Coupon PU002M0000001: ALLOCATED -> REDEEMED". */
    String getSummary();
}
