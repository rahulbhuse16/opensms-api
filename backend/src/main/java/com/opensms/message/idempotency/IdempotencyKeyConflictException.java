package com.opensms.message.idempotency;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException() {
        super("Idempotency-Key was already used with a different message request.");
    }
}
