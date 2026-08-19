package com.escrow.exception;

public class TransactionNotFoundException extends NotFoundException {
    public TransactionNotFoundException(Long id) {
        super("Transaction not found with id: " + id);
    }
}
