package com.opensms.analytics.dto;

import com.opensms.message.entity.MessageStatus;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record MessageAnalyticsSummaryResponse(
        long totalMessages,
        Map<MessageStatus, Long> messagesByStatus,
        LocalDate periodStart,
        LocalDate periodEnd,
        long periodTotalMessages,
        Map<MessageStatus, Long> periodMessagesByStatus
) {
    public MessageAnalyticsSummaryResponse {
        messagesByStatus = immutableStatusMap(messagesByStatus);
        periodMessagesByStatus = immutableStatusMap(periodMessagesByStatus);
    }

    private static Map<MessageStatus, Long> immutableStatusMap(Map<MessageStatus, Long> source) {
        EnumMap<MessageStatus, Long> copy = new EnumMap<>(MessageStatus.class);
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }
}
