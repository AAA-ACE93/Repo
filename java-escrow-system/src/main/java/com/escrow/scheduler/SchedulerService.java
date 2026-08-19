package com.escrow.scheduler;

import com.escrow.model.DisputeStatus;
import com.escrow.model.Transaction;
import com.escrow.model.TransactionStatus;
import com.escrow.repository.DisputeRepository;
import com.escrow.repository.TransactionRepository;
import com.escrow.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final TransactionRepository transactionRepository;
    private final DisputeRepository disputeRepository;
    private final TransactionService transactionService;

    public SchedulerService(TransactionRepository transactionRepository,
                            DisputeRepository disputeRepository,
                            TransactionService transactionService) {
        this.transactionRepository = transactionRepository;
        this.disputeRepository = disputeRepository;
        this.transactionService = transactionService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void processAutoReleases() {
        Instant now = Instant.now();
        List<Transaction> candidates = transactionRepository.findByStatusAndDeadlineBefore(TransactionStatus.FUNDED, now);

        for (Transaction tx : candidates) {
            try {
                boolean hasActiveDispute = disputeRepository.existsByTransactionIdAndStatusIn(
                        tx.getId(), List.of(DisputeStatus.OPEN, DisputeStatus.IN_PROGRESS));
                if (!hasActiveDispute) {
                    transactionService.autoRelease(tx);
                    log.debug("Auto-released transaction id: {}", tx.getId());
                } else {
                    log.debug("Skipping transaction id {} - has active dispute", tx.getId());
                }
            } catch (Exception e) {
                log.error("Error auto-releasing transaction id {}: {}", tx.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Pure eligibility check extracted for property-based testing.
     * Returns true iff the transaction should be auto-released.
     */
    public static boolean isEligible(Transaction tx, boolean hasActiveDispute) {
        return tx.getStatus() == TransactionStatus.FUNDED
                && tx.getDeadline().isBefore(Instant.now())
                && !hasActiveDispute;
    }
}
