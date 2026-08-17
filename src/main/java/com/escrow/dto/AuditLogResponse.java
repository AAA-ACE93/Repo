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
public class AuditLogResponse {
    private UUID id;
    private UUID userId;
    private String eventType;
    private String entityType;
    private String entityId;
    private String previousState;
    private String newState;
    private String ipAddress;
    private String correlationId;
    private OffsetDateTime createdAt;
}
