package com.opensms.webhook.dto;

import com.opensms.message.entity.Message;
import com.opensms.message.entity.MessageStatus;
import com.opensms.webhook.entity.WebhookEventType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WebhookEventPayload(
        int version,
        UUID eventId,
        WebhookEventType eventType,
        Instant occurredAt,
        UUID messageId,
        UUID tenantId,
        String destinationPhoneNumber,
        MessageStatus messageStatus
) {
    public WebhookEventPayload {
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported webhook payload version.");
        }
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(destinationPhoneNumber, "destinationPhoneNumber must not be null");
        Objects.requireNonNull(messageStatus, "messageStatus must not be null");
    }

    public static WebhookEventPayload fromMessage(Message message, WebhookEventType eventType) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        UUID messageId = message.getId();
        UUID tenantId = message.getTenant().getId();
        UUID eventId = stableEventId(messageId, eventType);
        return new WebhookEventPayload(1, eventId, eventType, Instant.now(), messageId, tenantId,
                message.getDestinationPhoneNumber(), message.getStatus());
    }

    public static UUID stableEventId(UUID messageId, WebhookEventType eventType) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        return UUID.nameUUIDFromBytes((messageId + ":" + eventType.value()).getBytes(StandardCharsets.UTF_8));
    }
}
