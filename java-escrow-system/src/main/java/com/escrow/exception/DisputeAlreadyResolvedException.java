package com.escrow.exception;

public class DisputeAlreadyResolvedException extends ConflictException {
    public DisputeAlreadyResolvedException(Long disputeId) {
        super("Dispute id " + disputeId + " has already been resolved");
    }
}
