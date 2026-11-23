package com.opensms.webhook.dto;

import com.opensms.webhook.entity.WebhookEventType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.util.List;

public record CreateWebhookSubscriptionRequest(
        @NotBlank(message = "URL is required.")
        @Size(max = 2048, message = "URL must not exceed 2048 characters.")
        String url,
        @NotEmpty(message = "Select at least one supported event type.")
        List<@NotNull(message = "Event types must not contain null values.") WebhookEventType> eventTypes
) {
    @AssertTrue(message = "URL must be a valid HTTPS endpoint without credentials or a fragment.")
    public boolean isValidHttpsUrl() {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.isAbsolute()
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null
                    && uri.getPort() <= 65535;
        } catch (Exception exception) {
            return false;
        }
    }
}
