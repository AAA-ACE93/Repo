package com.escrow.service;

import com.escrow.domain.entity.AuditLog;
import com.escrow.domain.entity.User;
import com.escrow.dto.AuditLogResponse;
import com.escrow.mapper.AuditLogMapper;
import com.escrow.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional
    public void logEvent(User user, String eventType, String entityType, String entityId,
                         String previousState, String newState, String ipAddress, String correlationId) {
        AuditLog logEntry = AuditLog.builder()
                .user(user)
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .previousState(previousState)
                .newState(newState)
                .ipAddress(ipAddress != null ? ipAddress : "127.0.0.1")
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .build();

        auditLogRepository.save(logEntry);
        log.info("[AUDIT LOG] Event: {} | Entity: {} ({}) | User: {}", eventType, entityType, entityId, user != null ? user.getEmail() : "SYSTEM");
    }

    @Override
    public Page<AuditLogResponse> getAuditLogsForEntity(String entityType, String entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable)
                .map(auditLogMapper::toResponse);
    }

    @Override
    public Page<AuditLogResponse> getAuditLogsForUser(UUID userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(auditLogMapper::toResponse);
    }

    @Override
    public Page<AuditLogResponse> getAllAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(auditLogMapper::toResponse);
    }
}
