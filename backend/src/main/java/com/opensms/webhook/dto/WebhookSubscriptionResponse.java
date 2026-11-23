package com.opensms.webhook.dto;

import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.entity.WebhookSubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WebhookSubscriptionResponse(
        UUID id,
        String url,
        List<WebhookEventType> eventTypes,
        WebhookSubscriptionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public WebhookSubscriptionResponse {
        eventTypes = List.copyOf(eventTypes);
    }
}
