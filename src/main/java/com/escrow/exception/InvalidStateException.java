package com.escrow.exception;

import org.springframework.http.HttpStatus;

public class InvalidStateException extends BaseException {
    public InvalidStateException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_ESCROW_STATE");
    }
}
