package com.opensms.outbox.service;

import com.opensms.outbox.entity.OutboxEvent;

import java.util.Map;
import java.util.UUID;

record OutboxMessageReference(UUID messageId, UUID tenantId) {

    static OutboxMessageReference from(OutboxEvent event) {
        if (!"MESSAGE_QUEUED".equals(event.getEventType())
                || !"MESSAGE".equals(event.getAggregateType())) {
            throw new IllegalArgumentException("Unexpected outbox event type.");
        }
        Map<String, Object> payload = event.getPayload();
        if (payload == null) {
            throw new IllegalArgumentException("Outbox payload is missing.");
        }
        UUID messageId = uuidValue(payload.get("messageId"));
        UUID tenantId = uuidValue(payload.get("tenantId"));
        if (!messageId.equals(event.getAggregateId())) {
            throw new IllegalArgumentException("Outbox aggregate and payload identifiers do not match.");
        }
        return new OutboxMessageReference(messageId, tenantId);
    }

    private static UUID uuidValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Outbox payload contains an invalid identifier.");
            }
        }
        throw new IllegalArgumentException("Outbox payload is missing a required identifier.");
    }
}
