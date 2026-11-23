package com.opensms.webhook.exception;

public class WebhookEncryptionConfigurationException extends RuntimeException {
    public WebhookEncryptionConfigurationException(String message) {
        super(message);
    }

    public WebhookEncryptionConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
