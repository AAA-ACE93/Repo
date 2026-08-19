package com.escrow.exception;

public class TransactionNotResolvableException extends UnprocessableEntityException {
    public TransactionNotResolvableException(Long transactionId) {
        super("Transaction id " + transactionId + " is not in a resolvable state (must be DISPUTED)");
    }
}
