package com.opensms.message.dto;

import com.opensms.message.entity.MessageStatus;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        String destinationPhoneNumber,
        String body,
        MessageStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
