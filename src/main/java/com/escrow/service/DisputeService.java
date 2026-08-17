package com.escrow.service;

import com.escrow.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DisputeService {
    DisputeResponse openDispute(UUID escrowId, CreateDisputeRequest request, String userEmail);

    DisputeEvidenceResponse attachEvidence(UUID disputeId, MultipartFile file, String userEmail);

    DisputeResponse getDisputeByEscrow(UUID escrowId, String userEmail);

    DisputeResponse getDisputeById(UUID disputeId, String userEmail);

    Page<DisputeResponse> getAllDisputes(Pageable pageable);

    DisputeResponse resolveDispute(UUID disputeId, ResolveDisputeRequest request, String adminEmail);
}
