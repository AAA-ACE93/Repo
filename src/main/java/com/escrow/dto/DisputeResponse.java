package com.escrow.dto;

import com.escrow.domain.enums.DisputeResolution;
import com.escrow.domain.enums.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeResponse {
    private UUID id;
    private UUID escrowId;
    private UserResponse openedBy;
    private String reason;
    private String description;
    private DisputeStatus status;
    private String adminDecision;
    private DisputeResolution resolution;
    private OffsetDateTime createdAt;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime updatedAt;
    private List<DisputeEvidenceResponse> evidence;
}
