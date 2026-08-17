package com.escrow.mapper;

import com.escrow.domain.entity.Dispute;
import com.escrow.domain.entity.DisputeEvidence;
import com.escrow.dto.DisputeEvidenceResponse;
import com.escrow.dto.DisputeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DisputeMapper {

    private final UserMapper userMapper;

    public DisputeResponse toResponse(Dispute dispute) {
        if (dispute == null) {
            return null;
        }
        return DisputeResponse.builder()
                .id(dispute.getId())
                .escrowId(dispute.getEscrow().getId())
                .openedBy(userMapper.toResponse(dispute.getOpenedBy()))
                .reason(dispute.getReason())
                .description(dispute.getDescription())
                .status(dispute.getStatus())
                .adminDecision(dispute.getAdminDecision())
                .resolution(dispute.getResolution())
                .createdAt(dispute.getCreatedAt())
                .resolvedAt(dispute.getResolvedAt())
                .updatedAt(dispute.getUpdatedAt())
                .evidence(dispute.getEvidenceList() != null ?
                        dispute.getEvidenceList().stream().map(this::toEvidenceResponse).collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    public DisputeEvidenceResponse toEvidenceResponse(DisputeEvidence evidence) {
        if (evidence == null) {
            return null;
        }
        return DisputeEvidenceResponse.builder()
                .id(evidence.getId())
                .disputeId(evidence.getDispute().getId())
                .uploadedBy(userMapper.toResponse(evidence.getUploadedBy()))
                .filename(evidence.getFilename())
                .contentType(evidence.getContentType())
                .fileSize(evidence.getFileSize())
                .storageKey(evidence.getStorageKey())
                .createdAt(evidence.getCreatedAt())
                .build();
    }
}
