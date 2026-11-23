package com.opensms.webhook.service;

import com.opensms.webhook.exception.WebhookEncryptionConfigurationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class WebhookSigningSecretService {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String encodedEncryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookSigningSecretService(
            @Value("${opensms.webhooks.encryption-key:}") String encodedEncryptionKey
    ) {
        this.encodedEncryptionKey = encodedEncryptionKey;
    }

    public EncryptedSecret generateAndEncrypt() {
        byte[] randomSecret = new byte[32];
        secureRandom.nextBytes(randomSecret);
        String plaintext = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomSecret);
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        return new EncryptedSecret(plaintext, encrypt(plaintext, iv), iv);
    }

    public byte[] encrypt(String plaintext, byte[] iv) {
        if (plaintext == null || iv == null || iv.length != IV_BYTES) {
            throw new IllegalArgumentException("Plaintext and a 12-byte AES-GCM IV are required.");
        }
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
            return cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new WebhookEncryptionConfigurationException("Unable to encrypt webhook signing secret.", exception);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] iv) {
        if (ciphertext == null || iv == null || iv.length != IV_BYTES) {
            throw new IllegalArgumentException("Ciphertext and a 12-byte AES-GCM IV are required.");
        }
        try {
            return new String(cipher(Cipher.DECRYPT_MODE, iv).doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new WebhookEncryptionConfigurationException("Unable to decrypt webhook signing secret.", exception);
        }
    }

    private Cipher cipher(int mode, byte[] iv) throws GeneralSecurityException {
        if (encodedEncryptionKey == null || encodedEncryptionKey.isBlank()) {
            throw new WebhookEncryptionConfigurationException(
                    "OPENSMS_WEBHOOK_ENCRYPTION_KEY is required and must be Base64 for a 32-byte AES-256 key.");
        }
        final byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedEncryptionKey);
        } catch (IllegalArgumentException exception) {
            throw new WebhookEncryptionConfigurationException(
                    "OPENSMS_WEBHOOK_ENCRYPTION_KEY must be valid Base64 for a 32-byte AES-256 key.", exception);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new WebhookEncryptionConfigurationException(
                    "OPENSMS_WEBHOOK_ENCRYPTION_KEY must decode to exactly 32 bytes for AES-256.");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher;
    }

    public record EncryptedSecret(String plaintext, byte[] ciphertext, byte[] iv) {
        public EncryptedSecret {
            ciphertext = ciphertext.clone();
            iv = iv.clone();
        }
        @Override public byte[] ciphertext() { return ciphertext.clone(); }
        @Override public byte[] iv() { return iv.clone(); }
        @Override public String toString() { return "EncryptedSecret[plaintext=[REDACTED], ciphertext=[REDACTED], iv=[REDACTED]]"; }
    }
}
