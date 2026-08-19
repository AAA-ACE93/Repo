package com.escrow.service;

import com.escrow.exception.*;
import com.escrow.model.*;
import com.escrow.repository.DisputeRepository;
import com.escrow.repository.EscrowAccountRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock private DisputeRepository disputeRepository;
    @Mock private EscrowAccountRepository escrowAccountRepository;
    @Mock private EscrowService escrowService;

    @InjectMocks
    private DisputeService disputeService;

    private User buyer;
    private User seller;
    private Transaction disputedTx;
    private Dispute openDispute;
    private EscrowAccount escrow;

    @BeforeEach
    void setUp() {
        buyer = new User("Alice", UserRole.BUYER, new BigDecimal("0.00"));
        buyer.setId(1L);
        seller = new User("Bob", UserRole.SELLER, new BigDecimal("0.00"));
        seller.setId(2L);

        disputedTx = new Transaction();
        disputedTx.setId(20L);
        disputedTx.setBuyer(buyer);
        disputedTx.setSeller(seller);
        disputedTx.setAmount(new BigDecimal("300.00"));
        disputedTx.setStatus(TransactionStatus.DISPUTED);
        disputedTx.setDeadline(Instant.now().plus(1, ChronoUnit.HOURS));

        escrow = new EscrowAccount(disputedTx, new BigDecimal("300.00"), EscrowStatus.LOCKED);
        escrow.setId(200L);

        openDispute = new Dispute();
        openDispute.setId(50L);
        openDispute.setTransaction(disputedTx);
        openDispute.setRaisedBy(buyer);
        openDispute.setReason("Item not delivered");
        openDispute.setStatus(DisputeStatus.OPEN);
    }

    @Test
    void resolveDispute_withRelease_creditsSellerAndSetsStatuses() {
        when(disputeRepository.findById(50L)).thenReturn(Optional.of(openDispute));
        when(escrowAccountRepository.findByTransactionId(20L)).thenReturn(Optional.of(escrow));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Dispute resolved = disputeService.resolveDispute(50L, DisputeResolution.RELEASE);

        verify(escrowService).releaseEscrow(eq(escrow), eq(seller));
        assertThat(disputedTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(resolved.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(resolved.getResolution()).isEqualTo(DisputeResolution.RELEASE);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void resolveDispute_withRefund_creditsBuyerAndSetsStatuses() {
        when(disputeRepository.findById(50L)).thenReturn(Optional.of(openDispute));
        when(escrowAccountRepository.findByTransactionId(20L)).thenReturn(Optional.of(escrow));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Dispute resolved = disputeService.resolveDispute(50L, DisputeResolution.REFUND);

        verify(escrowService).refundEscrow(eq(escrow), eq(buyer));
        assertThat(disputedTx.getStatus()).isEqualTo(TransactionStatus.REFUNDED);
        assertThat(resolved.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(resolved.getResolution()).isEqualTo(DisputeResolution.REFUND);
    }

    @Test
    void resolveDispute_whenAlreadyResolved_throwsDisputeAlreadyResolvedException() {
        openDispute.setStatus(DisputeStatus.RESOLVED);
        when(disputeRepository.findById(50L)).thenReturn(Optional.of(openDispute));

        assertThatThrownBy(() -> disputeService.resolveDispute(50L, DisputeResolution.RELEASE))
                .isInstanceOf(DisputeAlreadyResolvedException.class);
    }

    @Test
    void resolveDispute_whenTransactionNotDisputed_throwsTransactionNotResolvableException() {
        disputedTx.setStatus(TransactionStatus.FUNDED);
        when(disputeRepository.findById(50L)).thenReturn(Optional.of(openDispute));

        assertThatThrownBy(() -> disputeService.resolveDispute(50L, DisputeResolution.RELEASE))
                .isInstanceOf(TransactionNotResolvableException.class);
    }

    @Test
    void getDisputeById_throwsDisputeNotFoundException_whenMissing() {
        when(disputeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disputeService.getDisputeById(999L))
                .isInstanceOf(DisputeNotFoundException.class);
    }
}
