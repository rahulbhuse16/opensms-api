package com.opensms.apikey.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class ApiKeyGenerator {

    private static final String KEY_PREFIX = "os_live_";
    private static final int RANDOM_BYTES = 32;
    private static final int STORED_PREFIX_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedApiKey generate() {
        byte[] randomBytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);

        String plaintext = KEY_PREFIX + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
        String prefix = plaintext.substring(0, STORED_PREFIX_LENGTH);
        String hash = hash(plaintext);

        return new GeneratedApiKey(plaintext, prefix, hash);
    }

    public String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public record GeneratedApiKey(String plaintext, String prefix, String hash) {

        @Override
        public String toString() {
            return "GeneratedApiKey[prefix=" + prefix + ", plaintext=<redacted>, hash=<redacted>]";
        }
    }
}

