package com.opensms.analytics.dto;

import com.opensms.message.entity.MessageStatus;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record DailyMessageVolumeResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<Day> days
) {
    public DailyMessageVolumeResponse {
        days = List.copyOf(days);
    }

    public record Day(LocalDate date, long totalMessages, Map<MessageStatus, Long> messagesByStatus) {
        public Day {
            EnumMap<MessageStatus, Long> copy = new EnumMap<>(MessageStatus.class);
            copy.putAll(messagesByStatus);
            messagesByStatus = Collections.unmodifiableMap(copy);
        }
    }
}
