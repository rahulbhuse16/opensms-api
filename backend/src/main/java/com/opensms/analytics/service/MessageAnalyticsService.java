package com.opensms.analytics.service;

import com.opensms.analytics.dto.DailyMessageVolumeResponse;
import com.opensms.analytics.dto.MessageAnalyticsSummaryResponse;
import com.opensms.analytics.repository.DailyStatusCountProjection;
import com.opensms.analytics.repository.StatusCountProjection;
import com.opensms.message.entity.MessageStatus;
import com.opensms.message.repository.MessageRepository;
import com.opensms.security.TenantPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class MessageAnalyticsService {

    private static final int DEFAULT_PERIOD_DAYS = 30;
    private static final int MAX_PERIOD_DAYS = 366;

    private final MessageRepository messageRepository;

    public MessageAnalyticsService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MessageAnalyticsSummaryResponse summary(TenantPrincipal principal, LocalDate from, LocalDate to) {
        UUID tenantId = tenantId(principal);
        DateRange range = resolveRange(from, to);
        Instant fromInclusive = range.start().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = range.end().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<MessageStatus, Long> allCounts = statusCounts(messageRepository.aggregateStatusCounts(tenantId));
        Map<MessageStatus, Long> periodCounts = statusCounts(
                messageRepository.aggregateStatusCountsForPeriod(tenantId, fromInclusive, toExclusive));
        long periodTotal = periodCounts.values().stream().mapToLong(Long::longValue).sum();
        return new MessageAnalyticsSummaryResponse(
                messageRepository.countByTenant_Id(tenantId), allCounts,
                range.start(), range.end(), periodTotal, periodCounts);
    }

    @Transactional(readOnly = true)
    public DailyMessageVolumeResponse dailyVolume(TenantPrincipal principal, LocalDate from, LocalDate to) {
        UUID tenantId = tenantId(principal);
        DateRange range = resolveRange(from, to);
        Instant fromInclusive = range.start().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = range.end().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<LocalDate, EnumMap<MessageStatus, Long>> countsByDate = new TreeMap<>();
        List<DailyStatusCountProjection> rows = messageRepository.aggregateDailyStatusCounts(
                tenantId, fromInclusive, toExclusive);
        for (DailyStatusCountProjection row : rows) {
            countsByDate.computeIfAbsent(row.getDay(), ignored -> zeroCounts())
                    .put(MessageStatus.valueOf(row.getStatus()), row.getMessageCount());
        }

        List<DailyMessageVolumeResponse.Day> days = range.start().datesUntil(range.end().plusDays(1))
                .map(date -> {
                    EnumMap<MessageStatus, Long> counts = countsByDate.getOrDefault(date, zeroCounts());
                    long total = counts.values().stream().mapToLong(Long::longValue).sum();
                    return new DailyMessageVolumeResponse.Day(date, total, counts);
                })
                .toList();
        return new DailyMessageVolumeResponse(range.start(), range.end(), days);
    }

    private DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if ((from != null && from.isAfter(today)) || (to != null && to.isAfter(today))) {
            throw new IllegalArgumentException("Analytics dates must not be in the future (UTC).");
        }
        LocalDate end = to == null ? today : to;
        LocalDate start;
        try {
            start = from == null ? end.minusDays(DEFAULT_PERIOD_DAYS - 1L) : from;
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("The requested analytics date range is invalid.");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("The from date must be on or before the to date.");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(start, end) + 1;
        if (inclusiveDays > MAX_PERIOD_DAYS) {
            throw new IllegalArgumentException("Analytics date ranges must not exceed 366 days.");
        }
        return new DateRange(start, end);
    }

    private Map<MessageStatus, Long> statusCounts(List<StatusCountProjection> rows) {
        EnumMap<MessageStatus, Long> counts = zeroCounts();
        for (StatusCountProjection row : rows) {
            counts.put(MessageStatus.valueOf(row.getStatus()), row.getMessageCount());
        }
        return counts;
    }

    private EnumMap<MessageStatus, Long> zeroCounts() {
        EnumMap<MessageStatus, Long> counts = new EnumMap<>(MessageStatus.class);
        for (MessageStatus status : MessageStatus.values()) {
            counts.put(status, 0L);
        }
        return counts;
    }

    private UUID tenantId(TenantPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Authenticated tenant principal is required.");
        }
        return principal.tenantId();
    }

    private record DateRange(LocalDate start, LocalDate end) { }
}
