package com.opensms.gateway.config;

import com.opensms.gateway.SmsGateway;
import com.opensms.gateway.mock.MockSmsGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsGatewayConfigurationTest {

    private final SmsGatewayConfiguration configuration = new SmsGatewayConfiguration();
    private final MockSmsGateway mockSmsGateway = new MockSmsGateway();
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SmsGatewayConfiguration.class);

    @Test
    void defaultsToMockGatewayWithoutExternalConfiguration() {
        contextRunner.run(context -> {
            assertTrue(context.isRunning());
            assertTrue(context.getBean(SmsGateway.class) instanceof MockSmsGateway);
        });
    }

    @Test
    void mockModeSelectsMockGateway() {
        SmsGateway gateway = configuration.smsGateway("MOCK", mockSmsGateway);

        assertSame(mockSmsGateway, gateway);
    }

    @Test
    void unsupportedModeFailsClearly() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> configuration.smsGateway("TWILIO", mockSmsGateway)
        );

        assertTrue(exception.getMessage().contains("Unsupported opensms.gateway.mode 'TWILIO'"));
        assertTrue(exception.getMessage().contains("Supported mode: MOCK"));
    }
}
