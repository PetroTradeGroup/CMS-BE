package com.couponnumbergenerator.dto.response;

import com.couponnumbergenerator.repository.projection.AuditEventRow;

import java.time.LocalDateTime;

/** One entry in the system-wide audit feed — GET /api/v1/audit. */
public record AuditEventResponse(
        String category,
        String action,
        String actor,
        LocalDateTime occurredAt,
        String referenceType,
        Long referenceId,
        String summary
) {
    public static AuditEventResponse from(AuditEventRow row) {
        return new AuditEventResponse(row.getCategory(), row.getAction(), row.getActor(), row.getOccurredAt(),
                row.getReferenceType(), row.getReferenceId(), row.getSummary());
    }
}
