package com.opensms.gateway;

public record SmsGatewayResult(
        boolean accepted,
        String providerMessageReference,
        String responseMessage,
        boolean simulated
) {
}
