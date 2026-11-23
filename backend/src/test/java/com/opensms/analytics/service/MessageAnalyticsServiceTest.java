package com.opensms.analytics.service;

import com.opensms.analytics.dto.DailyMessageVolumeResponse;
import com.opensms.analytics.dto.MessageAnalyticsSummaryResponse;
import com.opensms.analytics.repository.DailyStatusCountProjection;
import com.opensms.analytics.repository.StatusCountProjection;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.repository.MessageRepository;
import com.opensms.security.TenantPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageAnalyticsServiceTest {

    @Mock private MessageRepository messageRepository;

    private MessageAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new MessageAnalyticsService(messageRepository);
    }

    @Test
    void emptyTenantReturnsZeroTotalsAndEveryActualStatus() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(messageRepository.aggregateStatusCounts(tenantId)).thenReturn(List.of());
        when(messageRepository.aggregateStatusCountsForPeriod(
                tenantId, today.minusDays(29).atStartOfDay(ZoneOffset.UTC).toInstant(),
                today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())).thenReturn(List.of());
        when(messageRepository.countByTenant_Id(tenantId)).thenReturn(0L);

        MessageAnalyticsSummaryResponse response = service.summary(principal(tenantId), null, null);

        assertEquals(0, response.totalMessages());
        assertEquals(0, response.periodTotalMessages());
        assertEquals(MessageStatus.values().length, response.messagesByStatus().size());
        assertEquals(MessageStatus.values().length, response.periodMessagesByStatus().size());
        for (MessageStatus status : MessageStatus.values()) {
            assertEquals(0L, response.messagesByStatus().get(status));
            assertEquals(0L, response.periodMessagesByStatus().get(status));
        }
        assertEquals(today.minusDays(29), response.periodStart());
        assertEquals(today, response.periodEnd());
    }

    @Test
    void summaryCalculatesLifetimeAndRequestedPeriodCountsForAuthenticatedTenant() {
        UUID tenantId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2025, 1, 2);
        LocalDate to = LocalDate.of(2025, 1, 4);
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant exclusiveEnd = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(messageRepository.aggregateStatusCounts(tenantId)).thenReturn(List.of(
                status("QUEUED", 3), status("SENT", 5), status("FAILED", 2)));
        when(messageRepository.aggregateStatusCountsForPeriod(tenantId, start, exclusiveEnd)).thenReturn(List.of(
                status("QUEUED", 2), status("SENT", 4)));
        when(messageRepository.countByTenant_Id(tenantId)).thenReturn(10L);

        MessageAnalyticsSummaryResponse response = service.summary(principal(tenantId), from, to);

        assertEquals(10, response.totalMessages());
        assertEquals(3L, response.messagesByStatus().get(MessageStatus.QUEUED));
        assertEquals(5L, response.messagesByStatus().get(MessageStatus.SENT));
        assertEquals(0L, response.messagesByStatus().get(MessageStatus.DELIVERED));
        assertEquals(6, response.periodTotalMessages());
        assertEquals(2L, response.periodMessagesByStatus().get(MessageStatus.QUEUED));
        assertEquals(4L, response.periodMessagesByStatus().get(MessageStatus.SENT));
        assertEquals(0L, response.periodMessagesByStatus().get(MessageStatus.FAILED));
        assertEquals(from, response.periodStart());
        assertEquals(to, response.periodEnd());
        verify(messageRepository).aggregateStatusCounts(tenantId);
        verify(messageRepository).aggregateStatusCountsForPeriod(tenantId, start, exclusiveEnd);
        verify(messageRepository).countByTenant_Id(tenantId);
    }

    @Test
    void dailyVolumeIsChronologicalAndFillsMissingDatesAndStatusesWithZero() {
        UUID tenantId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2025, 2, 1);
        LocalDate to = LocalDate.of(2025, 2, 3);
        Instant start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant exclusiveEnd = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(messageRepository.aggregateDailyStatusCounts(tenantId, start, exclusiveEnd)).thenReturn(List.of(
                daily(to, "FAILED", 1), daily(from, "QUEUED", 2), daily(from, "SIMULATED", 1)));

        DailyMessageVolumeResponse response = service.dailyVolume(principal(tenantId), from, to);

        assertEquals(List.of(from, from.plusDays(1), to), response.days().stream()
                .map(DailyMessageVolumeResponse.Day::date).toList());
        assertEquals(3, response.days().get(0).totalMessages());
        assertEquals(2L, response.days().get(0).messagesByStatus().get(MessageStatus.QUEUED));
        assertEquals(1L, response.days().get(0).messagesByStatus().get(MessageStatus.SIMULATED));
        assertEquals(0, response.days().get(1).totalMessages());
        assertEquals(MessageStatus.values().length, response.days().get(1).messagesByStatus().size());
        assertEquals(1, response.days().get(2).totalMessages());
        verify(messageRepository).aggregateDailyStatusCounts(tenantId, start, exclusiveEnd);
    }

    @Test
    void invalidFutureReversedAndOversizedRangesAreRejectedBeforeQueries() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        TenantPrincipal principal = principal(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> service.summary(principal, today.plusDays(1), today));
        assertThrows(IllegalArgumentException.class,
                () -> service.dailyVolume(principal, today, today.minusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.dailyVolume(principal, today.minusDays(366), today));

        verifyNoInteractions(messageRepository);
    }

    @Test
    void missingPrincipalIsRejectedAndLargeCountsUseAggregatesNotEntityCollections() {
        assertThrows(IllegalArgumentException.class, () -> service.summary(null, null, null));
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(messageRepository.aggregateDailyStatusCounts(
                org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(messageRepository.aggregateStatusCounts(tenantId)).thenReturn(List.of(status("SENT", 1_000_000)));
        when(messageRepository.aggregateStatusCountsForPeriod(
                org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(status("SENT", 250_000)));
        when(messageRepository.countByTenant_Id(tenantId)).thenReturn(1_000_000L);

        service.dailyVolume(principal(tenantId), today, today);
        MessageAnalyticsSummaryResponse summary = service.summary(principal(tenantId), null, null);

        assertEquals(1_000_000L, summary.totalMessages());
        assertEquals(250_000L, summary.periodTotalMessages());
        verify(messageRepository, never()).findAll();
        verify(messageRepository, never()).findAll(org.mockito.ArgumentMatchers.any(
                org.springframework.data.domain.Pageable.class));
    }

    private StatusCountProjection status(String status, long count) {
        return new StatusCountProjection() {
            @Override public String getStatus() { return status; }
            @Override public Long getMessageCount() { return count; }
        };
    }

    private DailyStatusCountProjection daily(LocalDate day, String status, long count) {
        return new DailyStatusCountProjection() {
            @Override public LocalDate getDay() { return day; }
            @Override public String getStatus() { return status; }
            @Override public Long getMessageCount() { return count; }
        };
    }

    private TenantPrincipal principal(UUID tenantId) {
        return new TenantPrincipal(tenantId, UUID.randomUUID());
    }
}
