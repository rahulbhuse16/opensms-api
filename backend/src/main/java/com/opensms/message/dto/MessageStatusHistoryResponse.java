package com.opensms.message.dto;

import com.opensms.message.entity.MessageStatus;

import java.time.Instant;
import java.util.UUID;

public record MessageStatusHistoryResponse(
        UUID id,
        UUID messageId,
        MessageStatus previousStatus,
        MessageStatus newStatus,
        String changeSource,
        Instant timestamp
) {
}
