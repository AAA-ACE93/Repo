package com.escrow.exception;

public class InsufficientFundsException extends BusinessRuleException {
    public InsufficientFundsException() {
        super("Buyer balance is insufficient to fund this transaction");
    }
}
