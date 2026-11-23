package com.opensms.webhook.mapper;

import com.opensms.webhook.dto.WebhookSubscriptionResponse;
import com.opensms.webhook.entity.WebhookSubscription;
import org.springframework.stereotype.Component;

@Component
public class WebhookSubscriptionMapper {

    public WebhookSubscriptionResponse toResponse(WebhookSubscription subscription) {
        if (subscription == null) {
            throw new IllegalArgumentException("Webhook subscription must not be null.");
        }
        return new WebhookSubscriptionResponse(
                subscription.getId(),
                subscription.getUrl(),
                subscription.getEventTypes(),
                subscription.getStatus(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt()
        );
    }
}
