package com.escrow.exception;

public class EscrowApplicationException extends RuntimeException {
    public EscrowApplicationException(String message) {
        super(message);
    }
    public EscrowApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
