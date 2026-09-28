package com.couponnumbergenerator.dto.request;

import com.couponnumbergenerator.enums.AuditCategory;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AuditFilterRequest(
        LocalDate dateFrom,
        LocalDate dateTo,
        /** Partial, case-insensitive match against who performed/decided the event. */
        String actor,
        AuditCategory category
) {
    public static AuditFilterRequest empty() {
        return new AuditFilterRequest(null, null, null, null);
    }

    /** Shared with {@link com.couponnumbergenerator.repository.AuditRepository}'s native-query params — both the paged feed and the unpaged export use the same translation. */
    public String categoryName() {
        return category == null ? null : category.name();
    }

    public String trimmedActor() {
        return actor == null || actor.isBlank() ? null : actor.trim();
    }

    public LocalDateTime fromTimestamp() {
        return dateFrom == null ? null : dateFrom.atStartOfDay();
    }

    /** Exclusive upper bound — the whole dateTo day is included, matching CouponSpecification's date filters. */
    public LocalDateTime toTimestampExclusive() {
        return dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();
    }
}
