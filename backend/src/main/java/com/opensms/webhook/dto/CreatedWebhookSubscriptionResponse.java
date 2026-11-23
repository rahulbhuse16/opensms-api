package com.opensms.webhook.dto;

import com.opensms.webhook.entity.WebhookEventType;
import com.opensms.webhook.entity.WebhookSubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreatedWebhookSubscriptionResponse(
        UUID id,
        String url,
        List<WebhookEventType> eventTypes,
        WebhookSubscriptionStatus status,
        Instant createdAt,
        Instant updatedAt,
        String signingSecret
) {
    public CreatedWebhookSubscriptionResponse {
        eventTypes = List.copyOf(eventTypes);
    }

    @Override
    public String toString() {
        return "CreatedWebhookSubscriptionResponse[id=" + id + ", url=" + url + ", eventTypes=" + eventTypes
                + ", status=" + status + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt
                + ", signingSecret=[REDACTED]]";
    }
}
