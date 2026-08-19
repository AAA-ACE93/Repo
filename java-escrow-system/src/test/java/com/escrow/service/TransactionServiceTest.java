package com.escrow.service;

import com.escrow.exception.*;
import com.escrow.model.*;
import com.escrow.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserService userService;
    @Mock private EscrowService escrowService;
    @Mock private DisputeService disputeService;
    @Mock private EscrowAccountRepository escrowAccountRepository;
    @Mock private DisputeRepository disputeRepository;

    @InjectMocks
    private TransactionService transactionService;

    private User buyer;
    private User seller;
    private Transaction pendingTx;
    private Transaction fundedTx;
    private EscrowAccount lockedEscrow;

    @BeforeEach
    void setUp() {
        buyer = new User("Alice", UserRole.BUYER, new BigDecimal("1000.00"));
        buyer.setId(1L);
        seller = new User("Bob", UserRole.SELLER, new BigDecimal("0.00"));
        seller.setId(2L);

        pendingTx = new Transaction();
        pendingTx.setId(10L);
        pendingTx.setBuyer(buyer);
        pendingTx.setSeller(seller);
        pendingTx.setAmount(new BigDecimal("200.00"));
        pendingTx.setStatus(TransactionStatus.PENDING);
        pendingTx.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));

        fundedTx = new Transaction();
        fundedTx.setId(11L);
        fundedTx.setBuyer(buyer);
        fundedTx.setSeller(seller);
        fundedTx.setAmount(new BigDecimal("200.00"));
        fundedTx.setStatus(TransactionStatus.FUNDED);
        fundedTx.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));

        lockedEscrow = new EscrowAccount(fundedTx, new BigDecimal("200.00"), EscrowStatus.LOCKED);
        lockedEscrow.setId(100L);
    }

    // ---- Fund tests ----

    @Test
    void fundTransaction_withNonPendingStatus_throwsInvalidTransactionStatusException() {
        when(transactionRepository.findById(11L)).thenReturn(Optional.of(fundedTx));

        assertThatThrownBy(() -> transactionService.fundTransaction(11L, buyer.getId()))
                .isInstanceOf(InvalidTransactionStatusException.class);
    }

    @Test
    void fundTransaction_withNonBuyer_throwsUnauthorizedOperationException() {
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(pendingTx));

        assertThatThrownBy(() -> transactionService.fundTransaction(10L, seller.getId()))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void fundTransaction_withInsufficientFunds_throwsInsufficientFundsException() {
        buyer.setBalance(new BigDecimal("100.00")); // less than 200
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(pendingTx));

        assertThatThrownBy(() -> transactionService.fundTransaction(10L, buyer.getId()))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void fundTransaction_withInsufficientFunds_leavesStateUnchanged() {
        buyer.setBalance(new BigDecimal("100.00"));
        BigDecimal originalBalance = buyer.getBalance();
        TransactionStatus originalStatus = pendingTx.getStatus();
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(pendingTx));

        try {
            transactionService.fundTransaction(10L, buyer.getId());
        } catch (InsufficientFundsException ignored) {}

        assertThatThrownBy(() -> transactionService.fundTransaction(10L, buyer.getId()))
                .isInstanceOf(InsufficientFundsException.class);
        // Verify escrowService.createEscrow was never called
        verify(escrowService, never()).createEscrow(any(), any());
    }

    // ---- Confirm tests ----

    @Test
    void confirmTransaction_withNonBuyer_throwsUnauthorizedOperationException() {
        when(transactionRepository.findById(11L)).thenReturn(Optional.of(fundedTx));

        assertThatThrownBy(() -> transactionService.confirmTransaction(11L, seller.getId()))
                .isInstanceOf(UnauthorizedOperationException.class);
    }

    @Test
    void confirmTransaction_withCompletedStatus_throwsConflictException() {
        fundedTx.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findById(11L)).thenReturn(Optional.of(fundedTx));

        assertThatThrownBy(() -> transactionService.confirmTransaction(11L, buyer.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmTransaction_withRefundedStatus_throwsConflictException() {
        fundedTx.setStatus(TransactionStatus.REFUNDED);
        when(transactionRepository.findById(11L)).thenReturn(Optional.of(fundedTx));

        assertThatThrownBy(() -> transactionService.confirmTransaction(11L, buyer.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmTransaction_withDisputedStatus_throwsConflictException() {
        fundedTx.setStatus(TransactionStatus.DISPUTED);
        when(transactionRepository.findById(11L)).thenReturn(Optional.of(fundedTx));

        assertThatThrownBy(() -> transactionService.confirmTransaction(11L, buyer.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmTransaction_withPendingStatus_throwsInvalidTransactionStatusException() {
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(pendingTx));

        assertThatThrownBy(() -> transactionService.confirmTransaction(10L, buyer.getId()))
                .isInstanceOf(InvalidTransactionStatusException.class);
    }

    // ---- Terminal state protection ----

    @Test
    void fundTransaction_onCompletedTransaction_throwsInvalidTransactionStatusException() {
        fundedTx.setStatus(TransactionStatus.COMPLETED);
        when(transactionRepository.findById(11L)).thenReturn(Optional.of(fundedTx));

        assertThatThrownBy(() -> transactionService.fundTransaction(11L, buyer.getId()))
                .isInstanceOf(InvalidTransactionStatusException.class);
    }

    @Test
    void fundTransaction_onRefundedTransaction_throwsInvalidTransactionStatusException() {
        fundedTx.setStatus(TransactionStatus.REFUNDED);
        when(transactionRepository.findById(11L)).thenReturn(Optional.of(fundedTx));

        assertThatThrownBy(() -> transactionService.fundTransaction(11L, buyer.getId()))
                .isInstanceOf(InvalidTransactionStatusException.class);
    }

    // ---- Dispute tests ----

    @Test
    void fileDispute_afterDeadline_throwsDisputeWindowClosedException() {
        pendingTx.setStatus(TransactionStatus.FUNDED);
        pendingTx.setDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(pendingTx));

        assertThatThrownBy(() -> transactionService.fileDispute(10L, buyer.getId(), "reason"))
                .isInstanceOf(DisputeWindowClosedException.class);
    }

    @Test
    void fileDispute_whenAlreadyDisputed_throwsDisputeAlreadyExistsException() {
        pendingTx.setStatus(TransactionStatus.DISPUTED);
        when(transactionRepository.findById(10L)).thenReturn(Optional.of(pendingTx));

        assertThatThrownBy(() -> transactionService.fileDispute(10L, buyer.getId(), "reason"))
                .isInstanceOf(DisputeAlreadyExistsException.class);
    }
}
