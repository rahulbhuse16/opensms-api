package com.opensms.message.idempotency;

import com.opensms.message.dto.CreateMessageRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageIdempotencySupportTest {

    private final MessageIdempotencySupport support = new MessageIdempotencySupport();

    @Test
    void fingerprintsAreDeterministicAndIncludeAllCreationFields() {
        CreateMessageRequest request = new CreateMessageRequest("+14155552671", "Hello");
        assertEquals(support.fingerprint(request), support.fingerprint(
                new CreateMessageRequest("+14155552671", "Hello")));
        assertNotEquals(support.fingerprint(request), support.fingerprint(
                new CreateMessageRequest("+14155552671", "Hello ")));
        assertNotEquals(support.fingerprint(request), support.fingerprint(
                new CreateMessageRequest("+14155552672", "Hello")));
        assertEquals(64, support.fingerprint(request).length());
    }

    @Test
    void validatesVisibleAsciiKeyAndMaximumLength() {
        assertEquals("abc-123", support.validateKey("abc-123"));
        assertEquals(128, support.validateKey("x".repeat(128)).length());
        assertThrows(IllegalArgumentException.class, () -> support.validateKey(""));
        assertThrows(IllegalArgumentException.class, () -> support.validateKey("has space"));
        assertThrows(IllegalArgumentException.class, () -> support.validateKey("x".repeat(129)));
    }
}
