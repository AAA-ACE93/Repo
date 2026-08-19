package com.escrow.exception;

public class DisputeWindowClosedException extends BusinessRuleException {
    public DisputeWindowClosedException() {
        super("The dispute window has closed; the deadline has passed");
    }
}
