package com.opensms.webhook.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum WebhookEventType {
    MESSAGE_QUEUED("message.queued"),
    MESSAGE_SIMULATED("message.simulated"),
    MESSAGE_SENT("message.sent"),
    MESSAGE_FAILED("message.failed");

    private final String value;

    WebhookEventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static WebhookEventType fromValue(String value) {
        return Arrays.stream(values())
                .filter(eventType -> eventType.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported webhook event type."));
    }
}
