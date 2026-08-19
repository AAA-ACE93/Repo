package com.escrow.service;

import com.escrow.exception.DisputeAlreadyResolvedException;
import com.escrow.exception.DisputeNotFoundException;
import com.escrow.exception.TransactionNotResolvableException;
import com.escrow.model.*;
import com.escrow.repository.DisputeRepository;
import com.escrow.repository.EscrowAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final EscrowAccountRepository escrowAccountRepository;
    private final EscrowService escrowService;

    public DisputeService(DisputeRepository disputeRepository,
                          EscrowAccountRepository escrowAccountRepository,
                          EscrowService escrowService) {
        this.disputeRepository = disputeRepository;
        this.escrowAccountRepository = escrowAccountRepository;
        this.escrowService = escrowService;
    }

    /**
     * Creates a new OPEN Dispute. Called within the caller's @Transactional boundary.
     */
    public Dispute createDispute(Transaction tx, User raisedBy, String reason) {
        Dispute dispute = new Dispute();
        dispute.setTransaction(tx);
        dispute.setRaisedBy(raisedBy);
        dispute.setReason(reason);
        dispute.setStatus(DisputeStatus.OPEN);
        return disputeRepository.save(dispute);
    }

    @Transactional
    public Dispute resolveDispute(Long disputeId, DisputeResolution resolution) {
        Dispute dispute = getDisputeById(disputeId);

        if (dispute.getStatus() == DisputeStatus.RESOLVED) {
            throw new DisputeAlreadyResolvedException(disputeId);
        }

        Transaction tx = dispute.getTransaction();
        if (tx.getStatus() != TransactionStatus.DISPUTED) {
            throw new TransactionNotResolvableException(tx.getId());
        }

        EscrowAccount escrow = escrowAccountRepository.findByTransactionId(tx.getId())
                .orElseThrow(() -> new IllegalStateException("No escrow account found for transaction: " + tx.getId()));

        if (resolution == DisputeResolution.RELEASE) {
            escrowService.releaseEscrow(escrow, tx.getSeller());
            tx.setStatus(TransactionStatus.COMPLETED);
        } else {
            escrowService.refundEscrow(escrow, tx.getBuyer());
            tx.setStatus(TransactionStatus.REFUNDED);
        }

        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolution(resolution);
        dispute.setResolvedAt(Instant.now());
        return disputeRepository.save(dispute);
    }

    public List<Dispute> getAllDisputes() {
        return disputeRepository.findAll();
    }

    public Dispute getDisputeById(Long id) {
        return disputeRepository.findById(id)
                .orElseThrow(() -> new DisputeNotFoundException(id));
    }
}
