package com.escrow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class FileDisputeRequest {

    @NotNull(message = "Raised-by user ID is required")
    private Long raisedByUserId;

    @NotBlank(message = "Reason is required")
    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

    public FileDisputeRequest() {}

    public FileDisputeRequest(Long raisedByUserId, String reason) {
        this.raisedByUserId = raisedByUserId;
        this.reason = reason;
    }

    public Long getRaisedByUserId() { return raisedByUserId; }
    public void setRaisedByUserId(Long raisedByUserId) { this.raisedByUserId = raisedByUserId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
