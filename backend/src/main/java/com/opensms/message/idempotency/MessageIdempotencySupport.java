package com.opensms.message.idempotency;

import com.opensms.message.dto.CreateMessageRequest;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
public class MessageIdempotencySupport {

    private static final int MAX_KEY_LENGTH = 128;
    private static final Pattern VALID_KEY = Pattern.compile("[\\x21-\\x7E]{1,128}");
    private static final byte[] FINGERPRINT_VERSION = "opensms-message-request-v1".getBytes(StandardCharsets.UTF_8);

    public String validateKey(String key) {
        if (key == null || key.length() > MAX_KEY_LENGTH || !VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain 1 to 128 visible ASCII characters with no whitespace.");
        }
        return key;
    }

    public String fingerprint(CreateMessageRequest request) {
        if (request == null || request.destinationPhoneNumber() == null || request.body() == null) {
            throw new IllegalArgumentException("A complete message request is required for fingerprinting.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateField(digest, FINGERPRINT_VERSION);
            // E.164 validation gives the phone field a canonical representation; body whitespace is meaningful.
            updateField(digest, request.destinationPhoneNumber().getBytes(StandardCharsets.UTF_8));
            updateField(digest, request.body().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void updateField(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }
}
