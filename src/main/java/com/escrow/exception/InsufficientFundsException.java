package com.escrow.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BaseException {
    public InsufficientFundsException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INSUFFICIENT_FUNDS");
    }
}
