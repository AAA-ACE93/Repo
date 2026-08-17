package com.escrow.service;

import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.domain.entity.Payment;
import com.escrow.domain.entity.User;
import com.escrow.domain.enums.EscrowStatus;
import com.escrow.domain.enums.PaymentStatus;
import com.escrow.domain.enums.Role;
import com.escrow.dto.*;
import com.escrow.exception.ConflictException;
import com.escrow.exception.InvalidStateException;
import com.escrow.exception.ResourceNotFoundException;
import com.escrow.exception.UnauthorizedAccessException;
import com.escrow.mapper.EscrowMapper;
import com.escrow.mapper.PaymentMapper;
import com.escrow.payment.PaymentGateway;
import com.escrow.repository.DisputeRepository;
import com.escrow.repository.EscrowTransactionRepository;
import com.escrow.repository.PaymentRepository;
import com.escrow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowServiceImpl implements EscrowService {

    private final EscrowTransactionRepository escrowRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final DisputeRepository disputeRepository;
    private final LedgerService ledgerService;
    private final PaymentGateway paymentGateway;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final EscrowMapper escrowMapper;
    private final PaymentMapper paymentMapper;

    @Value("${app.escrow.auto-release-delay-days:14}")
    private int autoReleaseDelayDays;

    @Override
    @Transactional
    public EscrowResponse createEscrow(CreateEscrowRequest request, String buyerEmail) {
        User buyer = userRepository.findByEmail(buyerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Buyer user not found"));

        User seller = userRepository.findById(request.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller user not found with ID: " + request.getSellerId()));

        if (buyer.getId().equals(seller.getId())) {
            throw new ConflictException("Buyer and seller cannot be the same user");
        }

        String refNo = "ESC-2026-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        EscrowTransaction escrow = EscrowTransaction.builder()
                .id(UUID.randomUUID())
                .referenceNumber(refNo)
                .buyer(buyer)
                .seller(seller)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .description(request.getDescription())
                .terms(request.getTerms())
                .releaseConditions(request.getReleaseConditions())
                .expectedCompletionDate(request.getExpectedCompletionDate() != null ?
                        request.getExpectedCompletionDate() : OffsetDateTime.now().plusDays(autoReleaseDelayDays))
                .status(EscrowStatus.AWAITING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();

        EscrowTransaction saved = escrowRepository.save(escrow);

        auditLogService.logEvent(buyer, "ESCROW_CREATED", "EscrowTransaction", saved.getId().toString(), null, saved.getStatus().name(), null, null);
        notificationService.sendNotification(seller, "New Escrow Created", "An escrow transaction " + saved.getReferenceNumber() + " has been created.", "ESCROW_CREATED", saved.getId());

        return escrowMapper.toResponse(saved);
    }

    @Override
    public EscrowResponse getEscrowById(UUID id, String userEmail) {
        EscrowTransaction escrow = escrowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found: " + id));

        validateAccessPermission(escrow, userEmail);
        return escrowMapper.toResponse(escrow);
    }

    @Override
    public EscrowResponse getEscrowByReference(String referenceNumber, String userEmail) {
        EscrowTransaction escrow = escrowRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found with reference: " + referenceNumber));

        validateAccessPermission(escrow, userEmail);
        return escrowMapper.toResponse(escrow);
    }

    @Override
    public Page<EscrowResponse> getUserEscrows(String userEmail, Pageable pageable) {
        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            return escrowRepository.findAll(pageable).map(escrowMapper::toResponse);
        }

        return escrowRepository.findByBuyerIdOrSellerId(user.getId(), user.getId(), pageable)
                .map(escrowMapper::toResponse);
    }

    @Override
    @Transactional
    public PaymentResponse fundEscrow(UUID id, FundEscrowRequest request, String buyerEmail) {
        EscrowTransaction escrow = escrowRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found: " + id));

        User buyer = userRepository.findByEmail(buyerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!escrow.getBuyer().getId().equals(buyer.getId())) {
            throw new UnauthorizedAccessException("Only the designated buyer can fund this escrow");
        }

        if (escrow.getStatus() != EscrowStatus.AWAITING_PAYMENT) {
            throw new InvalidStateException("Cannot fund escrow in status: " + escrow.getStatus());
        }

        if (request.getAmount().compareTo(escrow.getAmount()) != 0) {
            throw new ConflictException("Funding amount (" + request.getAmount() + ") does not match escrow amount (" + escrow.getAmount() + ")");
        }

        Payment payment = paymentGateway.createPayment(escrow, buyer, request.getAmount(), request.getIdempotencyKey());
        Payment capturedPayment = paymentGateway.capturePayment(payment.getPaymentReference());

        // Update Escrow State
        escrow.setStatus(EscrowStatus.FUNDED);
        escrow.setPaymentStatus(PaymentStatus.CAPTURED);
        escrow.setFundedAt(OffsetDateTime.now());
        escrowRepository.save(escrow);

        // Record Double-Entry Ledger Entry
        ledgerService.recordBuyerPayment(escrow, escrow.getAmount());

        auditLogService.logEvent(buyer, "ESCROW_FUNDED", "EscrowTransaction", escrow.getId().toString(), EscrowStatus.AWAITING_PAYMENT.name(), EscrowStatus.FUNDED.name(), null, null);
        notificationService.sendNotification(escrow.getSeller(), "Escrow Funded", "Escrow " + escrow.getReferenceNumber() + " has been funded by buyer.", "ESCROW_FUNDED", escrow.getId());

        return paymentMapper.toResponse(capturedPayment);
    }

    @Override
    @Transactional
    public void processPaymentWebhook(PaymentWebhookRequest request) {
        boolean success = paymentGateway.processWebhook(request);
        if (success && "payment.succeeded".equalsIgnoreCase(request.getEventType())) {
            Payment payment = paymentRepository.findByPaymentReference(request.getPaymentReference())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

            EscrowTransaction escrow = escrowRepository.findByIdWithLock(payment.getEscrow().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Escrow not found"));

            if (escrow.getStatus() == EscrowStatus.AWAITING_PAYMENT) {
                escrow.setStatus(EscrowStatus.FUNDED);
                escrow.setPaymentStatus(PaymentStatus.CAPTURED);
                escrow.setFundedAt(OffsetDateTime.now());
                escrowRepository.save(escrow);

                ledgerService.recordBuyerPayment(escrow, escrow.getAmount());
                notificationService.sendNotification(escrow.getSeller(), "Escrow Funded via Webhook", "Payment received for escrow " + escrow.getReferenceNumber(), "ESCROW_FUNDED", escrow.getId());
            }
        }
    }

    @Override
    @Transactional
    public EscrowResponse startInProgress(UUID id, String userEmail) {
        EscrowTransaction escrow = escrowRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found: " + id));

        validateAccessPermission(escrow, userEmail);

        if (escrow.getStatus() != EscrowStatus.FUNDED) {
            throw new InvalidStateException("Cannot start in-progress state from status: " + escrow.getStatus());
        }

        EscrowStatus prev = escrow.getStatus();
        escrow.setStatus(EscrowStatus.IN_PROGRESS);
        EscrowTransaction saved = escrowRepository.save(escrow);

        User actor = userRepository.findByEmail(userEmail).orElse(null);
        auditLogService.logEvent(actor, "ESCROW_IN_PROGRESS", "EscrowTransaction", saved.getId().toString(), prev.name(), saved.getStatus().name(), null, null);

        return escrowMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public EscrowResponse requestRelease(UUID id, String sellerEmail) {
        EscrowTransaction escrow = escrowRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found: " + id));

        User seller = userRepository.findByEmail(sellerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!escrow.getSeller().getId().equals(seller.getId())) {
            throw new UnauthorizedAccessException("Only the seller can request release");
        }

        if (escrow.getStatus() != EscrowStatus.FUNDED && escrow.getStatus() != EscrowStatus.IN_PROGRESS) {
            throw new InvalidStateException("Cannot request release from status: " + escrow.getStatus());
        }

        EscrowStatus prev = escrow.getStatus();
        escrow.setStatus(EscrowStatus.AWAITING_RELEASE);
        EscrowTransaction saved = escrowRepository.save(escrow);

        auditLogService.logEvent(seller, "RELEASE_REQUESTED", "EscrowTransaction", saved.getId().toString(), prev.name(), saved.getStatus().name(), null, null);
        notificationService.sendNotification(escrow.getBuyer(), "Release Requested", "Seller requested fund release for escrow " + escrow.getReferenceNumber(), "RELEASE_REQUESTED", escrow.getId());

        return escrowMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public EscrowResponse releaseEscrow(UUID id, String buyerEmail) {
        EscrowTransaction escrow = escrowRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found: " + id));

        User buyer = userRepository.findByEmail(buyerEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!escrow.getBuyer().getId().equals(buyer.getId())) {
            throw new UnauthorizedAccessException("Only the buyer can approve fund release");
        }

        return executeRelease(escrow, buyer);
    }

    @Override
    @Transactional
    public EscrowResponse adminReleaseEscrow(UUID id, String adminEmail) {
        EscrowTransaction escrow = escrowRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found: " + id));

        User admin = userRepository.findByEmail(adminEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return executeRelease(escrow, admin);
    }

    private EscrowResponse executeRelease(EscrowTransaction escrow, User actor) {
        if (escrow.getStatus() == EscrowStatus.RELEASED) {
            throw new InvalidStateException("Escrow funds have already been released");
        }

        if (escrow.getStatus() == EscrowStatus.REFUNDED) {
            throw new InvalidStateException("Cannot release funds for a refunded escrow");
        }

        if (escrow.getStatus() == EscrowStatus.DISPUTED) {
            throw new InvalidStateException("Cannot release funds while escrow is disputed without dispute resolution");
        }

        if (escrow.getStatus() != EscrowStatus.FUNDED && escrow.getStatus() != EscrowStatus.IN_PROGRESS && escrow.getStatus() != EscrowStatus.AWAITING_RELEASE) {
            throw new InvalidStateException("Cannot release funds from status: " + escrow.getStatus());
        }

        EscrowStatus prev = escrow.getStatus();
        escrow.setStatus(EscrowStatus.RELEASED);
        escrow.setReleasedAt(OffsetDateTime.now());
        escrow.setCompletedAt(OffsetDateTime.now());

        EscrowTransaction saved = escrowRepository.save(escrow);

        // Immutable double-entry ledger entry
        ledgerService.recordEscrowRelease(saved, saved.getAmount());

        auditLogService.logEvent(actor, "FUNDS_RELEASED", "EscrowTransaction", saved.getId().toString(), prev.name(), saved.getStatus().name(), null, null);
        notificationService.sendNotification(escrow.getSeller(), "Funds Released", "Funds of " + saved.getAmount() + " " + saved.getCurrency() + " have been released to you for escrow " + saved.getReferenceNumber(), "FUNDS_RELEASED", saved.getId());

        return escrowMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public EscrowResponse refundEscrow(UUID id, String userEmail, String reason) {
        EscrowTransaction escrow = escrowRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found: " + id));

        User actor = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!escrow.getBuyer().getId().equals(actor.getId()) && !escrow.getSeller().getId().equals(actor.getId()) && actor.getRole() != Role.ADMIN) {
            throw new UnauthorizedAccessException("Not authorized to refund this escrow");
        }

        if (escrow.getStatus() == EscrowStatus.RELEASED) {
            throw new InvalidStateException("Cannot refund an escrow that has already been released");
        }

        if (escrow.getStatus() == EscrowStatus.REFUNDED) {
            throw new InvalidStateException("Escrow has already been refunded");
        }

        if (escrow.getStatus() != EscrowStatus.FUNDED && escrow.getStatus() != EscrowStatus.IN_PROGRESS && escrow.getStatus() != EscrowStatus.AWAITING_RELEASE && escrow.getStatus() != EscrowStatus.DISPUTED) {
            throw new InvalidStateException("Cannot refund escrow from status: " + escrow.getStatus());
        }

        EscrowStatus prev = escrow.getStatus();
        escrow.setStatus(EscrowStatus.REFUNDED);
        escrow.setCompletedAt(OffsetDateTime.now());

        EscrowTransaction saved = escrowRepository.save(escrow);

        // Immutable double-entry compensating ledger transaction
        ledgerService.recordEscrowRefund(saved, saved.getAmount());

        auditLogService.logEvent(actor, "REFUND_CREATED", "EscrowTransaction", saved.getId().toString(), prev.name(), saved.getStatus().name(), null, null);
        notificationService.sendNotification(escrow.getBuyer(), "Escrow Refunded", "Refund of " + saved.getAmount() + " " + saved.getCurrency() + " processed for escrow " + saved.getReferenceNumber() + ". Reason: " + reason, "REFUND_CREATED", saved.getId());

        return escrowMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public EscrowResponse cancelEscrow(UUID id, String userEmail) {
        EscrowTransaction escrow = escrowRepository.findByIdWithLock(id)
                .orElseThrow(() -> new ResourceNotFoundException("Escrow transaction not found: " + id));

        User actor = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!escrow.getBuyer().getId().equals(actor.getId()) && !escrow.getSeller().getId().equals(actor.getId()) && actor.getRole() != Role.ADMIN) {
            throw new UnauthorizedAccessException("Not authorized to cancel this escrow");
        }

        if (escrow.getStatus() != EscrowStatus.CREATED && escrow.getStatus() != EscrowStatus.AWAITING_PAYMENT) {
            throw new InvalidStateException("Cannot cancel escrow once funds have been deposited. Current status: " + escrow.getStatus());
        }

        EscrowStatus prev = escrow.getStatus();
        escrow.setStatus(EscrowStatus.CANCELLED);
        EscrowTransaction saved = escrowRepository.save(escrow);

        auditLogService.logEvent(actor, "ESCROW_CANCELLED", "EscrowTransaction", saved.getId().toString(), prev.name(), saved.getStatus().name(), null, null);

        return escrowMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public int processAutomaticReleases() {
        OffsetDateTime cutoffTime = OffsetDateTime.now().minusDays(autoReleaseDelayDays);
        List<EscrowTransaction> eligibleList = escrowRepository.findByStatusAndExpectedCompletionDateBefore(EscrowStatus.AWAITING_RELEASE, cutoffTime);

        int count = 0;
        for (EscrowTransaction escrow : eligibleList) {
            boolean hasDispute = disputeRepository.findByEscrowId(escrow.getId()).isPresent();
            if (!hasDispute && escrow.getStatus() != EscrowStatus.DISPUTED) {
                executeRelease(escrow, escrow.getBuyer());
                count++;
            }
        }
        return count;
    }

    private void validateAccessPermission(EscrowTransaction escrow, String userEmail) {
        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == Role.ADMIN) {
            return;
        }

        if (!escrow.getBuyer().getId().equals(user.getId()) && !escrow.getSeller().getId().equals(user.getId())) {
            throw new UnauthorizedAccessException("Access denied to escrow transaction " + escrow.getReferenceNumber());
        }
    }
}
