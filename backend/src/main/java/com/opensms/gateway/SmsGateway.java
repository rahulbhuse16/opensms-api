package com.opensms.gateway;

public interface SmsGateway {

    /**
     * Submits a message to the configured gateway. Acceptance does not imply mobile-network delivery.
     */
    SmsGatewayResult submit(SmsGatewayRequest request);
}
