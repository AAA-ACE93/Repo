package com.escrow.exception;

public class UnauthorizedOperationException extends AccessDeniedException {
    public UnauthorizedOperationException(String message) {
        super(message);
    }
}
