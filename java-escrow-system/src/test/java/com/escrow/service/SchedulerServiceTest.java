package com.escrow.service;

import com.escrow.model.*;
import com.escrow.repository.DisputeRepository;
import com.escrow.repository.TransactionRepository;
import com.escrow.scheduler.SchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private DisputeRepository disputeRepository;
    @Mock private TransactionService transactionService;

    @InjectMocks
    private SchedulerService schedulerService;

    private User buyer;
    private User seller;

    @BeforeEach
    void setUp() {
        buyer = new User("Alice", UserRole.BUYER, new BigDecimal("0.00"));
        buyer.setId(1L);
        seller = new User("Bob", UserRole.SELLER, new BigDecimal("0.00"));
        seller.setId(2L);
    }

    private Transaction makeFundedOverdue() {
        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setBuyer(buyer);
        tx.setSeller(seller);
        tx.setAmount(new BigDecimal("100.00"));
        tx.setStatus(TransactionStatus.FUNDED);
        tx.setDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        return tx;
    }

    @Test
    void processAutoReleases_autoReleasesEligibleTransaction() {
        Transaction tx = makeFundedOverdue();
        when(transactionRepository.findByStatusAndDeadlineBefore(eq(TransactionStatus.FUNDED), any()))
                .thenReturn(List.of(tx));
        when(disputeRepository.existsByTransactionIdAndStatusIn(eq(1L), anyList()))
                .thenReturn(false);

        schedulerService.processAutoReleases();

        verify(transactionService).autoRelease(tx);
    }

    @Test
    void processAutoReleases_skipsDisputedTransaction() {
        Transaction tx = makeFundedOverdue();
        when(transactionRepository.findByStatusAndDeadlineBefore(eq(TransactionStatus.FUNDED), any()))
                .thenReturn(List.of(tx));
        when(disputeRepository.existsByTransactionIdAndStatusIn(eq(1L), anyList()))
                .thenReturn(true);

        schedulerService.processAutoReleases();

        verify(transactionService, never()).autoRelease(any());
    }

    @Test
    void processAutoReleases_continuesAfterErrorInOneTransaction() {
        Transaction tx1 = makeFundedOverdue();
        tx1.setId(1L);
        Transaction tx2 = makeFundedOverdue();
        tx2.setId(2L);

        when(transactionRepository.findByStatusAndDeadlineBefore(eq(TransactionStatus.FUNDED), any()))
                .thenReturn(List.of(tx1, tx2));
        when(disputeRepository.existsByTransactionIdAndStatusIn(anyLong(), anyList()))
                .thenReturn(false);
        doThrow(new RuntimeException("DB error")).when(transactionService).autoRelease(tx1);

        schedulerService.processAutoReleases();

        verify(transactionService).autoRelease(tx1);
        verify(transactionService).autoRelease(tx2);
    }

    @Test
    void isEligible_returnsTrueForFundedOverdueWithoutDispute() {
        Transaction tx = makeFundedOverdue();
        assert SchedulerService.isEligible(tx, false);
    }

    @Test
    void isEligible_returnsFalseWhenHasActiveDispute() {
        Transaction tx = makeFundedOverdue();
        assert !SchedulerService.isEligible(tx, true);
    }

    @Test
    void isEligible_returnsFalseWhenDeadlineInFuture() {
        Transaction tx = new Transaction();
        tx.setBuyer(buyer);
        tx.setSeller(seller);
        tx.setAmount(new BigDecimal("100.00"));
        tx.setStatus(TransactionStatus.FUNDED);
        tx.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));
        assert !SchedulerService.isEligible(tx, false);
    }

    @Test
    void isEligible_returnsFalseWhenStatusNotFunded() {
        Transaction tx = makeFundedOverdue();
        tx.setStatus(TransactionStatus.PENDING);
        assert !SchedulerService.isEligible(tx, false);
    }
}
