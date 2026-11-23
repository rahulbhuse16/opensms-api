package com.opensms.apikey.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    void generatesDifferentKeysOnRepeatedCalls() {
        ApiKeyGenerator.GeneratedApiKey first = generator.generate();
        ApiKeyGenerator.GeneratedApiKey second = generator.generate();

        assertNotEquals(first.plaintext(), second.plaintext());
    }

    @Test
    void usesExpectedPrefix() {
        ApiKeyGenerator.GeneratedApiKey generated = generator.generate();

        assertTrue(generated.plaintext().startsWith("os_live_"));
        assertEquals(16, generated.prefix().length());
        assertTrue(generated.plaintext().startsWith(generated.prefix()));
    }

    @Test
    void usesUrlSafeBase64EncodingWithoutPaddingForAtLeast32RandomBytes() {
        ApiKeyGenerator.GeneratedApiKey generated = generator.generate();
        String encodedSecret = generated.plaintext().substring("os_live_".length());

        assertTrue(encodedSecret.matches("[A-Za-z0-9_-]+"));
        assertFalse(encodedSecret.contains("="));
        assertEquals(32, Base64.getUrlDecoder().decode(encodedSecret).length);
    }

    @Test
    void produces64CharacterSha256HexHash() {
        ApiKeyGenerator.GeneratedApiKey generated = generator.generate();

        assertTrue(generated.hash().matches("[0-9a-f]{64}"));
    }

    @Test
    void hashMatchesTheGeneratedPlaintextKey() throws Exception {
        ApiKeyGenerator.GeneratedApiKey generated = generator.generate();
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(generated.plaintext().getBytes(StandardCharsets.UTF_8));

        assertEquals(HexFormat.of().formatHex(digest), generated.hash());
    }

    @Test
    void generatedResultToStringDoesNotExposePlaintextOrHash() {
        ApiKeyGenerator.GeneratedApiKey generated = generator.generate();
        String description = generated.toString();

        assertFalse(description.contains(generated.plaintext()));
        assertFalse(description.contains(generated.hash()));
    }
}
