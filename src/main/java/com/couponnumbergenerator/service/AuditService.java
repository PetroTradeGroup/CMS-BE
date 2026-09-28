package com.couponnumbergenerator.service;

import com.couponnumbergenerator.dto.request.AuditFilterRequest;
import com.couponnumbergenerator.dto.response.AuditEventResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

public interface AuditService {

    /**
     * The system-wide audit feed, newest first: every coupon lifecycle event and every decided
     * approval request (approvals, rejections, and everything in between), merged into one
     * chronological list. See {@link com.couponnumbergenerator.repository.AuditRepository}.
     */
    PagedResponse<AuditEventResponse> getAuditEvents(AuditFilterRequest filter, Pageable pageable);
}
