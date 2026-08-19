package com.escrow.exception;

public class UserNotFoundException extends NotFoundException {
    public UserNotFoundException(Long id) {
        super("User not found with id: " + id);
    }
}
