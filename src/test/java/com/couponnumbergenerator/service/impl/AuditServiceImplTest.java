package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.request.AuditFilterRequest;
import com.couponnumbergenerator.dto.response.AuditEventResponse;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.AuditCategory;
import com.couponnumbergenerator.repository.AuditRepository;
import com.couponnumbergenerator.repository.projection.AuditEventRow;
import com.couponnumbergenerator.service.BulkConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock private AuditRepository auditRepository;
    @Mock private BulkConfigService bulkConfigService;
    @Mock private AuditEventRow row;

    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditServiceImpl(auditRepository, bulkConfigService);
        lenient().when(bulkConfigService.getMaxPageSize()).thenReturn(100);
    }

    @Test
    void mapsFilterAndPageableIntoRepositoryCallAndComputesPaging() {
        when(row.getCategory()).thenReturn("COUPON_LIFECYCLE");
        when(row.getAction()).thenReturn("REDEMPTION");
        when(row.getActor()).thenReturn("attendant1");
        when(row.getOccurredAt()).thenReturn(LocalDateTime.of(2026, 9, 16, 10, 0));
        when(row.getReferenceType()).thenReturn("COUPON");
        when(row.getReferenceId()).thenReturn(1L);
        when(row.getSummary()).thenReturn("Coupon PU001M0000001: ALLOCATED -> REDEEMED");

        when(auditRepository.findAuditEvents(eq("APPROVAL_DECISION"), eq("super"), any(), any(), eq(20), eq(0L)))
                .thenReturn(List.of(row));
        when(auditRepository.countAuditEvents(eq("APPROVAL_DECISION"), eq("super"), any(), any())).thenReturn(45L);

        AuditFilterRequest filter = new AuditFilterRequest(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 16), "super", AuditCategory.APPROVAL_DECISION);
        PagedResponse<AuditEventResponse> response = service.getAuditEvents(filter, PageRequest.of(0, 20));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().summary()).isEqualTo("Coupon PU001M0000001: ALLOCATED -> REDEEMED");
        assertThat(response.totalElements()).isEqualTo(45);
        assertThat(response.totalPages()).isEqualTo(3); // ceil(45/20)
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.last()).isFalse();

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        org.mockito.Mockito.verify(auditRepository).findAuditEvents(
                eq("APPROVAL_DECISION"), eq("super"), fromCaptor.capture(), toCaptor.capture(), eq(20), eq(0L));
        assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2026, 9, 1).atStartOfDay());
        // dateTo is inclusive of the whole day, so the bound passed down is exclusive of the next day.
        assertThat(toCaptor.getValue()).isEqualTo(LocalDate.of(2026, 9, 17).atStartOfDay());
    }

    @Test
    void noFilterPassesNullsAndMarksLastPageWhenExhausted() {
        when(auditRepository.findAuditEvents(eq(null), eq(null), eq(null), eq(null), eq(20), eq(20L)))
                .thenReturn(List.of());
        when(auditRepository.countAuditEvents(eq(null), eq(null), eq(null), eq(null))).thenReturn(5L);

        PagedResponse<AuditEventResponse> response =
                service.getAuditEvents(AuditFilterRequest.empty(), PageRequest.of(1, 20));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.last()).isTrue();
    }

    @Test
    void clampsPageSizeToConfiguredMax() {
        when(bulkConfigService.getMaxPageSize()).thenReturn(50);
        when(auditRepository.findAuditEvents(any(), any(), any(), any(), eq(50), anyLong())).thenReturn(List.of());
        when(auditRepository.countAuditEvents(any(), any(), any(), any())).thenReturn(0L);

        PagedResponse<AuditEventResponse> response =
                service.getAuditEvents(AuditFilterRequest.empty(), PageRequest.of(0, 500));

        assertThat(response.size()).isEqualTo(50);
    }
}
