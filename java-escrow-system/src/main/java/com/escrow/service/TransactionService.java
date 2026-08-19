package com.escrow.service;

import com.escrow.dto.CreateTransactionRequest;
import com.escrow.exception.*;
import com.escrow.model.*;
import com.escrow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final EscrowService escrowService;
    private final DisputeService disputeService;
    private final EscrowAccountRepository escrowAccountRepository;
    private final DisputeRepository disputeRepository;

    public TransactionService(TransactionRepository transactionRepository,
                               UserService userService,
                               EscrowService escrowService,
                               DisputeService disputeService,
                               EscrowAccountRepository escrowAccountRepository,
                               DisputeRepository disputeRepository) {
        this.transactionRepository = transactionRepository;
        this.userService = userService;
        this.escrowService = escrowService;
        this.disputeService = disputeService;
        this.escrowAccountRepository = escrowAccountRepository;
        this.disputeRepository = disputeRepository;
    }

    @Transactional
    public Transaction createTransaction(CreateTransactionRequest request) {
        User buyer = userService.getUserById(request.getBuyerId());
        User seller = userService.getUserById(request.getSellerId());

        if (buyer.getRole() != UserRole.BUYER) {
            throw new InvalidRoleException("User id " + request.getBuyerId() + " does not have BUYER role");
        }
        if (seller.getRole() != UserRole.SELLER) {
            throw new InvalidRoleException("User id " + request.getSellerId() + " does not have SELLER role");
        }
        if (buyer.getId().equals(seller.getId())) {
            throw new BusinessRuleException("Buyer and Seller must be different users");
        }

        User arbitrator = null;
        if (request.getArbitratorId() != null) {
            arbitrator = userService.getUserById(request.getArbitratorId());
            if (arbitrator.getRole() != UserRole.ARBITRATOR) {
                throw new InvalidRoleException("User id " + request.getArbitratorId() + " does not have ARBITRATOR role");
            }
        }

        Transaction tx = new Transaction();
        tx.setBuyer(buyer);
        tx.setSeller(seller);
        tx.setArbitrator(arbitrator);
        tx.setAmount(request.getAmount());
        tx.setDeadline(request.getDeadline());
        tx.setStatus(TransactionStatus.PENDING);
        return transactionRepository.save(tx);
    }

    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    public List<Transaction> getTransactionsByUserId(Long userId) {
        return transactionRepository.findByBuyerIdOrSellerIdOrArbitratorId(userId, userId, userId);
    }

    @Transactional
    public Transaction fundTransaction(Long txId, Long requestingUserId) {
        Transaction tx = getTransactionById(txId);

        if (!tx.getBuyer().getId().equals(requestingUserId)) {
            throw new UnauthorizedOperationException("Only the Buyer may fund this transaction");
        }
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new InvalidTransactionStatusException(
                    "Transaction cannot be funded in status: " + tx.getStatus() + ". Must be PENDING.");
        }

        User buyer = tx.getBuyer();
        if (buyer.getBalance().compareTo(tx.getAmount()) < 0) {
            throw new InsufficientFundsException();
        }

        buyer.setBalance(buyer.getBalance().subtract(tx.getAmount()));
        escrowService.createEscrow(tx, tx.getAmount());
        tx.setStatus(TransactionStatus.FUNDED);
        return transactionRepository.save(tx);
    }

    @Transactional
    public Transaction confirmTransaction(Long txId, Long requestingUserId) {
        Transaction tx = getTransactionById(txId);

        if (!tx.getBuyer().getId().equals(requestingUserId)) {
            throw new UnauthorizedOperationException("Only the Buyer may confirm this transaction");
        }

        TransactionStatus status = tx.getStatus();
        if (status == TransactionStatus.COMPLETED || status == TransactionStatus.REFUNDED) {
            throw new ConflictException("Transaction id " + txId + " is already in terminal state: " + status);
        }
        if (status == TransactionStatus.DISPUTED) {
            throw new ConflictException("Transaction id " + txId + " is under dispute and cannot be confirmed");
        }
        if (status != TransactionStatus.FUNDED) {
            throw new InvalidTransactionStatusException(
                    "Transaction cannot be confirmed in status: " + status + ". Must be FUNDED.");
        }

        EscrowAccount escrow = escrowAccountRepository.findByTransactionId(txId)
                .orElseThrow(() -> new IllegalStateException("No escrow account found for transaction: " + txId));

        escrowService.releaseEscrow(escrow, tx.getSeller());
        tx.setStatus(TransactionStatus.COMPLETED);
        return transactionRepository.save(tx);
    }

    @Transactional
    public Dispute fileDispute(Long txId, Long raisedByUserId, String reason) {
        Transaction tx = getTransactionById(txId);

        if (tx.getStatus() == TransactionStatus.DISPUTED) {
            throw new DisputeAlreadyExistsException(txId);
        }
        if (tx.getStatus() != TransactionStatus.FUNDED) {
            throw new InvalidTransactionStatusException(
                    "Disputes can only be filed for FUNDED transactions. Current status: " + tx.getStatus());
        }
        if (!Instant.now().isBefore(tx.getDeadline())) {
            throw new DisputeWindowClosedException();
        }

        boolean isBuyer = tx.getBuyer().getId().equals(raisedByUserId);
        boolean isSeller = tx.getSeller().getId().equals(raisedByUserId);
        if (!isBuyer && !isSeller) {
            throw new UnauthorizedOperationException("Only the Buyer or Seller may file a dispute");
        }

        User raisedBy = isBuyer ? tx.getBuyer() : tx.getSeller();
        Dispute dispute = disputeService.createDispute(tx, raisedBy, reason);
        tx.setStatus(TransactionStatus.DISPUTED);
        transactionRepository.save(tx);
        return dispute;
    }

    @Transactional
    public Transaction autoRelease(Transaction tx) {
        // Re-fetch to get latest state (idempotency guard)
        Transaction freshTx = getTransactionById(tx.getId());
        if (freshTx.getStatus() != TransactionStatus.FUNDED) {
            log.debug("AutoRelease skipped for transaction {}: status is {}", tx.getId(), freshTx.getStatus());
            return freshTx;
        }

        EscrowAccount escrow = escrowAccountRepository.findByTransactionId(freshTx.getId())
                .orElseThrow(() -> new IllegalStateException("No escrow account for transaction: " + freshTx.getId()));

        escrowService.releaseEscrow(escrow, freshTx.getSeller());
        freshTx.setStatus(TransactionStatus.COMPLETED);
        return transactionRepository.save(freshTx);
    }
}
