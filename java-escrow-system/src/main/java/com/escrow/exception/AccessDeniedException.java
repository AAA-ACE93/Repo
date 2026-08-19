package com.escrow.exception;

public class AccessDeniedException extends EscrowApplicationException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
