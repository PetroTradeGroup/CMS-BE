package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.AuditFilterRequest;
import com.couponnumbergenerator.dto.response.AuditEventResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.repository.AuditRepository;
import com.couponnumbergenerator.service.AuditService;
import com.couponnumbergenerator.service.BulkConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditRepository auditRepository;
    private final BulkConfigService bulkConfigService;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AuditEventResponse> getAuditEvents(AuditFilterRequest filter, Pageable pageable) {
        int size = clampPageSize(pageable.getPageSize());
        int page = pageable.getPageNumber();
        String category = filter.categoryName();
        LocalDateTime from = filter.fromTimestamp();
        LocalDateTime to = filter.toTimestampExclusive();
        String actor = filter.trimmedActor();

        List<AuditEventResponse> content = auditRepository
                .findAuditEvents(category, actor, from, to, size, (long) page * size)
                .stream()
                .map(AuditEventResponse::from)
                .toList();
        long totalElements = auditRepository.countAuditEvents(category, actor, from, to);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean last = page >= totalPages - 1;

        return new PagedResponse<>(content, page, size, totalElements, totalPages, last);
    }

    private int clampPageSize(int requestedSize) {
        int max = bulkConfigService.getMaxPageSize();
        return Math.min(requestedSize, max);
    }
}
