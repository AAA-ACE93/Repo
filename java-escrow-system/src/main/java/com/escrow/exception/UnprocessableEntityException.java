package com.escrow.exception;

public class UnprocessableEntityException extends EscrowApplicationException {
    public UnprocessableEntityException(String message) {
        super(message);
    }
}
