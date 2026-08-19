package com.escrow.exception;

public class DisputeAlreadyExistsException extends ConflictException {
    public DisputeAlreadyExistsException(Long transactionId) {
        super("A dispute has already been filed for transaction id: " + transactionId);
    }
}
