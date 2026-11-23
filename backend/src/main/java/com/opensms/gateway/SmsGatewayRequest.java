package com.opensms.gateway;

import java.util.Objects;
import java.util.UUID;

public record SmsGatewayRequest(UUID messageId, String destinationPhoneNumber, String body) {

    public SmsGatewayRequest {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(destinationPhoneNumber, "destinationPhoneNumber must not be null");
        Objects.requireNonNull(body, "body must not be null");
    }
}
