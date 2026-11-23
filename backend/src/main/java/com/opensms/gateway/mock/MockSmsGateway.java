package com.opensms.gateway.mock;

import com.opensms.gateway.SmsGateway;
import com.opensms.gateway.SmsGatewayRequest;
import com.opensms.gateway.SmsGatewayResult;

public class MockSmsGateway implements SmsGateway {

    @Override
    public SmsGatewayResult submit(SmsGatewayRequest request) {
        String simulatedReference = "mock-" + request.messageId();
        return new SmsGatewayResult(
                true,
                simulatedReference,
                "SIMULATED_ACCEPTED: no SMS was sent to or delivered by a mobile network.",
                true
        );
    }
}
