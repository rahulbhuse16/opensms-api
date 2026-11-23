package com.opensms.gateway.mock;

import com.opensms.gateway.SmsGatewayRequest;
import com.opensms.gateway.SmsGatewayResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockSmsGatewayTest {

    @Test
    void returnsDeterministicAcceptedResultClearlyMarkedAsSimulation() {
        MockSmsGateway gateway = new MockSmsGateway();
        UUID messageId = UUID.fromString("64cb7b94-6aa0-4f55-b8c1-8469fcf552cc");

        SmsGatewayResult result = gateway.submit(
                new SmsGatewayRequest(messageId, "+14155552671", "Hello"));

        assertTrue(result.accepted());
        assertTrue(result.simulated());
        assertEquals("mock-" + messageId, result.providerMessageReference());
        assertEquals(
                "SIMULATED_ACCEPTED: no SMS was sent to or delivered by a mobile network.",
                result.responseMessage()
        );
        assertEquals(result.providerMessageReference(), gateway.submit(
                new SmsGatewayRequest(messageId, "+14155552671", "Hello"))
                .providerMessageReference());
    }
}
