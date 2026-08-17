package com.escrow.dto;

import com.escrow.domain.enums.DisputeResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDisputeRequest {

    @NotNull(message = "Resolution is required")
    private DisputeResolution resolution;

    @NotBlank(message = "Admin decision explanation is required")
    private String adminDecision;
}
