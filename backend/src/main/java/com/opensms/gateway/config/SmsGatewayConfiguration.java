package com.opensms.gateway.config;

import com.opensms.gateway.SmsGateway;
import com.opensms.gateway.SmsGatewayMode;
import com.opensms.gateway.mock.MockSmsGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class SmsGatewayConfiguration {

    @Bean
    public MockSmsGateway mockSmsGateway() {
        return new MockSmsGateway();
    }

    @Bean
    public SmsGateway smsGateway(
            @Value("${opensms.gateway.mode:MOCK}") String configuredMode,
            MockSmsGateway mockSmsGateway
    ) {
        final SmsGatewayMode mode;
        try {
            mode = SmsGatewayMode.valueOf(configuredMode.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Unsupported opensms.gateway.mode '" + configuredMode + "'. Supported mode: MOCK.",
                    exception
            );
        }

        return switch (mode) {
            case MOCK -> mockSmsGateway;
        };
    }
}
