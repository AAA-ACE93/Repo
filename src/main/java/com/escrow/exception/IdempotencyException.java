package com.escrow.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyException extends BaseException {
    public IdempotencyException(String message) {
        super(message, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");
    }
}
