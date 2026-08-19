package com.escrow.exception;

public class InvalidTransactionStatusException extends BusinessRuleException {
    public InvalidTransactionStatusException(String message) {
        super(message);
    }
}
