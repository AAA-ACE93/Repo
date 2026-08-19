package com.escrow.exception;

public class DisputeNotFoundException extends NotFoundException {
    public DisputeNotFoundException(Long id) {
        super("Dispute not found with id: " + id);
    }
}
