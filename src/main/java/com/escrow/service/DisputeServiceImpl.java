package com.escrow.service;

import com.escrow.domain.entity.Dispute;
import com.escrow.domain.entity.DisputeEvidence;
import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.domain.entity.User;
import com.escrow.domain.enums.DisputeResolution;
import com.escrow.domain.enums.DisputeStatus;
import com.escrow.domain.enums.EscrowStatus;
import com.escrow.domain.enums.Role;
import com.escrow.dto.*;
import com.escrow.exception.ConflictException;
import com.escrow.exception.InvalidStateException;
import com.escrow.exception.ResourceNotFoundException;
import com.escrow.exception.UnauthorizedAccessException;
import com.escrow.integration.storage.StorageService;
import com.escrow.mapper.DisputeMapper;
import com.escrow.repository.DisputeEvidenceRepository;
import com.escrow.repository.DisputeRepository;
import com.escrow.repository.EscrowTransactionRepository;
import com.escrow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final EscrowTransactionRepository escrowRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final StorageService storageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final DisputeMapper disputeMapper;

    @Override
    @Transactional
    public DisputeResponse openDispute(UUID escrowId, CreateDisputeRequest request, String userEmail) {
        EscrowTransaction escrow = escrowRepository.findByIdWithLock(escrowId)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found: " + escrowId));

        User openedBy = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!escrow.getBuyer().getId().equals(openedBy.getId()) && !escrow.getSeller().getId().equals(openedBy.getId())) {
            throw new UnauthorizedAccessException("Only parties to the escrow transaction can open a dispute");
        }

        if (escrow.getStatus() == EscrowStatus.RELEASED || escrow.getStatus() == EscrowStatus.REFUNDED || escrow.getStatus() == EscrowStatus.CANCELLED) {
            throw new InvalidStateException("Cannot open a dispute on a completed or cancelled transaction");
        }

        if (disputeRepository.findByEscrowId(escrowId).isPresent()) {
            throw new ConflictException("A dispute already exists for this escrow transaction");
        }

        Dispute dispute = Dispute.builder()
                .escrow(escrow)
                .openedBy(openedBy)
                .reason(request.getReason())
                .description(request.getDescription())
                .status(DisputeStatus.OPEN)
                .build();

        Dispute savedDispute = disputeRepository.save(dispute);

        EscrowStatus prev = escrow.getStatus();
        escrow.setStatus(EscrowStatus.DISPUTED);
        escrowRepository.save(escrow);

        User notifyTarget = escrow.getBuyer().getId().equals(openedBy.getId()) ? escrow.getSeller() : escrow.getBuyer();

        auditLogService.logEvent(openedBy, "DISPUTE_OPENED", "Dispute", savedDispute.getId().toString(), prev.name(), EscrowStatus.DISPUTED.name(), null, null);
        notificationService.sendNotification(notifyTarget, "Dispute Opened", "A dispute has been opened on escrow " + escrow.getReferenceNumber(), "DISPUTE_OPENED", savedDispute.getId());

        return disputeMapper.toResponse(savedDispute);
    }

    @Override
    @Transactional
    public DisputeEvidenceResponse attachEvidence(UUID disputeId, MultipartFile file, String userEmail) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + disputeId));

        User uploader = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        EscrowTransaction escrow = dispute.getEscrow();
        if (!escrow.getBuyer().getId().equals(uploader.getId()) && !escrow.getSeller().getId().equals(uploader.getId()) && uploader.getRole() != Role.ADMIN) {
            throw new UnauthorizedAccessException("Not authorized to upload evidence to this dispute");
        }

        if (dispute.getStatus() == DisputeStatus.CLOSED || dispute.getStatus() == DisputeStatus.RESOLVED_BUYER || dispute.getStatus() == DisputeStatus.RESOLVED_SELLER) {
            throw new InvalidStateException("Cannot attach evidence to a resolved or closed dispute");
        }

        String storageKey = storageService.storeFile(file, "disputes/" + disputeId);

        DisputeEvidence evidence = DisputeEvidence.builder()
                .dispute(dispute)
                .uploadedBy(uploader)
                .filename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "evidence_file")
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .fileSize(file.getSize())
                .storageKey(storageKey)
                .build();

        DisputeEvidence savedEvidence = evidenceRepository.save(evidence);

        auditLogService.logEvent(uploader, "EVIDENCE_ATTACHED", "DisputeEvidence", savedEvidence.getId().toString(), null, savedEvidence.getFilename(), null, null);

        return disputeMapper.toEvidenceResponse(savedEvidence);
    }

    @Override
    public DisputeResponse getDisputeByEscrow(UUID escrowId, String userEmail) {
        Dispute dispute = disputeRepository.findByEscrowId(escrowId)
                .orElseThrow(() -> new ResourceNotFoundException("No dispute found for escrow: " + escrowId));

        validateDisputeAccess(dispute, userEmail);
        return disputeMapper.toResponse(dispute);
    }

    @Override
    public DisputeResponse getDisputeById(UUID disputeId, String userEmail) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + disputeId));

        validateDisputeAccess(dispute, userEmail);
        return disputeMapper.toResponse(dispute);
    }

    @Override
    public Page<DisputeResponse> getAllDisputes(Pageable pageable) {
        return disputeRepository.findAll(pageable).map(disputeMapper::toResponse);
    }

    @Override
    @Transactional
    public DisputeResponse resolveDispute(UUID disputeId, ResolveDisputeRequest request, String adminEmail) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + disputeId));

        User admin = userRepository.findByEmail(adminEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (dispute.getStatus() == DisputeStatus.CLOSED || dispute.getStatus() == DisputeStatus.RESOLVED_BUYER || dispute.getStatus() == DisputeStatus.RESOLVED_SELLER) {
            throw new InvalidStateException("Dispute is already resolved or closed");
        }

        EscrowTransaction escrow = escrowRepository.findByIdWithLock(dispute.getEscrow().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found"));

        dispute.setAdminDecision(request.getAdminDecision());
        dispute.setResolution(request.getResolution());
        dispute.setResolvedAt(OffsetDateTime.now());

        if (request.getResolution() == DisputeResolution.REFUND_BUYER) {
            dispute.setStatus(DisputeStatus.RESOLVED_BUYER);
            escrow.setStatus(EscrowStatus.REFUNDED);
            escrow.setCompletedAt(OffsetDateTime.now());
            ledgerService.recordEscrowRefund(escrow, escrow.getAmount());
        } else if (request.getResolution() == DisputeResolution.RELEASE_SELLER) {
            dispute.setStatus(DisputeStatus.RESOLVED_SELLER);
            escrow.setStatus(EscrowStatus.RELEASED);
            escrow.setReleasedAt(OffsetDateTime.now());
            escrow.setCompletedAt(OffsetDateTime.now());
            ledgerService.recordEscrowRelease(escrow, escrow.getAmount());
        } else if (request.getResolution() == DisputeResolution.SPLIT) {
            dispute.setStatus(DisputeStatus.PARTIALLY_RESOLVED);
            escrow.setStatus(EscrowStatus.RELEASED);
            escrow.setCompletedAt(OffsetDateTime.now());

            BigDecimal half = escrow.getAmount().divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal secondHalf = escrow.getAmount().subtract(half);
            ledgerService.recordDisputeSplit(escrow, half, secondHalf);
        }

        escrowRepository.save(escrow);
        Dispute savedDispute = disputeRepository.save(dispute);

        auditLogService.logEvent(admin, "DISPUTE_RESOLVED", "Dispute", savedDispute.getId().toString(), DisputeStatus.OPEN.name(), savedDispute.getStatus().name(), null, null);

        notificationService.sendNotification(escrow.getBuyer(), "Dispute Resolved", "Dispute on escrow " + escrow.getReferenceNumber() + " resolved: " + request.getResolution(), "DISPUTE_RESOLVED", savedDispute.getId());
        notificationService.sendNotification(escrow.getSeller(), "Dispute Resolved", "Dispute on escrow " + escrow.getReferenceNumber() + " resolved: " + request.getResolution(), "DISPUTE_RESOLVED", savedDispute.getId());

        return disputeMapper.toResponse(savedDispute);
    }

    private void validateDisputeAccess(Dispute dispute, String userEmail) {
        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            return;
        }

        EscrowTransaction escrow = dispute.getEscrow();
        if (!escrow.getBuyer().getId().equals(user.getId()) && !escrow.getSeller().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("Access denied to dispute " + dispute.getId());
        }
    }
}
