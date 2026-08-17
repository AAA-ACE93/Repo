package com.escrow.service;

import com.escrow.domain.entity.User;
import com.escrow.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditLogService {
    void logEvent(User user, String eventType, String entityType, String entityId, String previousState, String newState, String ipAddress, String correlationId);
    Page<AuditLogResponse> getAuditLogsForEntity(String entityType, String entityId, Pageable pageable);
    Page<AuditLogResponse> getAuditLogsForUser(UUID userId, Pageable pageable);
    Page<AuditLogResponse> getAllAuditLogs(Pageable pageable);
}
