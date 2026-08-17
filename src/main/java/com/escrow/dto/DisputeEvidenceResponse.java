package com.escrow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEvidenceResponse {
    private UUID id;
    private UUID disputeId;
    private UserResponse uploadedBy;
    private String filename;
    private String contentType;
    private Long fileSize;
    private String storageKey;
    private OffsetDateTime createdAt;
}
