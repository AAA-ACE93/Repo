package com.escrow.dto;

import com.escrow.model.DisputeResolution;
import jakarta.validation.constraints.NotNull;

public class ResolveDisputeRequest {

    @NotNull(message = "Resolution is required (RELEASE or REFUND)")
    private DisputeResolution resolution;

    public ResolveDisputeRequest() {}

    public ResolveDisputeRequest(DisputeResolution resolution) {
        this.resolution = resolution;
    }

    public DisputeResolution getResolution() { return resolution; }
    public void setResolution(DisputeResolution resolution) { this.resolution = resolution; }
}
