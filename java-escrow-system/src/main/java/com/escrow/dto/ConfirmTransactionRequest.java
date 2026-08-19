package com.escrow.dto;

import jakarta.validation.constraints.NotNull;

public class ConfirmTransactionRequest {

    @NotNull(message = "Requesting user ID is required")
    private Long requestingUserId;

    public ConfirmTransactionRequest() {}

    public ConfirmTransactionRequest(Long requestingUserId) {
        this.requestingUserId = requestingUserId;
    }

    public Long getRequestingUserId() { return requestingUserId; }
    public void setRequestingUserId(Long requestingUserId) { this.requestingUserId = requestingUserId; }
}
