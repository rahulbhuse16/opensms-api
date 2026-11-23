package com.opensms.webhook.service;

import com.opensms.webhook.exception.WebhookEncryptionConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSigningSecretServiceTest {

    private final String validKey = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void generatesUniquePrefixedSecretsWithFreshIvAndRoundTripsEncryption() {
        WebhookSigningSecretService service = new WebhookSigningSecretService(validKey);

        var first = service.generateAndEncrypt();
        var second = service.generateAndEncrypt();

        assertTrue(first.plaintext().startsWith("whsec_"));
        assertNotEquals(first.plaintext(), second.plaintext());
        assertFalse(Arrays.equals(first.iv(), second.iv()), "Each secret must use a fresh IV.");
        assertNotEquals(first.plaintext(), new String(first.ciphertext(), java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(first.plaintext(), service.decrypt(first.ciphertext(), first.iv()));
        assertEquals(second.plaintext(), service.decrypt(second.ciphertext(), second.iv()));
    }

    @Test
    void rejectsMissingMalformedAndWrongLengthEncryptionKeys() {
        assertThrows(WebhookEncryptionConfigurationException.class,
                () -> new WebhookSigningSecretService("").generateAndEncrypt());
        assertThrows(WebhookEncryptionConfigurationException.class,
                () -> new WebhookSigningSecretService("not-base64!").generateAndEncrypt());
        assertThrows(WebhookEncryptionConfigurationException.class,
                () -> new WebhookSigningSecretService(Base64.getEncoder().encodeToString(new byte[16]))
                        .generateAndEncrypt());
    }

}
