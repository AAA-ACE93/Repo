package com.escrow.mapper;

import com.escrow.domain.entity.AuditLog;
import com.escrow.dto.AuditLogResponse;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog auditLog) {
        if (auditLog == null) {
            return null;
        }
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .userId(auditLog.getUser() != null ? auditLog.getUser().getId() : null)
                .eventType(auditLog.getEventType())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .previousState(auditLog.getPreviousState())
                .newState(auditLog.getNewState())
                .ipAddress(auditLog.getIpAddress())
                .correlationId(auditLog.getCorrelationId())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
