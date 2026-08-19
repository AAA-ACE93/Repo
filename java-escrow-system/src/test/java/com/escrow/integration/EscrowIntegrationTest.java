package com.escrow.integration;

import com.escrow.dto.CreateTransactionRequest;
import com.escrow.dto.CreateUserRequest;
import com.escrow.model.*;
import com.escrow.repository.EscrowAccountRepository;
import com.escrow.repository.UserRepository;
import com.escrow.scheduler.SchedulerService;
import com.escrow.service.DisputeService;
import com.escrow.service.TransactionService;
import com.escrow.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests (Tasks 13.1 - 13.4)
 */
@SpringBootTest
@ActiveProfiles("test")
class EscrowIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private TransactionService transactionService;
    @Autowired private DisputeService disputeService;
    @Autowired private UserRepository userRepository;
    @Autowired private EscrowAccountRepository escrowAccountRepository;
    @Autowired private SchedulerService schedulerService;

    /**
     * Task 13.1: create users → create transaction → fund → confirm (happy path)
     */
    @Test
    @Transactional
    void happyPath_fundAndConfirm() {
        User buyer = userService.createUser(new CreateUserRequest("IntBuyer", UserRole.BUYER, new BigDecimal("1000.00")));
        User seller = userService.createUser(new CreateUserRequest("IntSeller", UserRole.SELLER, new BigDecimal("0.00")));

        Instant deadline = Instant.now().plus(30, ChronoUnit.MINUTES);
        Transaction tx = transactionService.createTransaction(
                new CreateTransactionRequest(buyer.getId(), seller.getId(), null, new BigDecimal("250.00"), deadline));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);

        transactionService.fundTransaction(tx.getId(), buyer.getId());

        User buyerAfterFund = userRepository.findById(buyer.getId()).orElseThrow();
        assertThat(buyerAfterFund.getBalance()).isEqualByComparingTo("750.00");

        EscrowAccount escrow = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();
        assertThat(escrow.getLockedAmount()).isEqualByComparingTo("250.00");
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.LOCKED);

        transactionService.confirmTransaction(tx.getId(), buyer.getId());

        User sellerAfterConfirm = userRepository.findById(seller.getId()).orElseThrow();
        assertThat(sellerAfterConfirm.getBalance()).isEqualByComparingTo("250.00");

        EscrowAccount escrowAfterConfirm = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();
        assertThat(escrowAfterConfirm.getStatus()).isEqualTo(EscrowStatus.RELEASED);

        Transaction completedTx = transactionService.getTransactionById(tx.getId());
        assertThat(completedTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
    }

    /**
     * Task 13.2: create users → create transaction → fund → dispute → resolve RELEASE
     */
    @Test
    @Transactional
    void fundDisputeAndResolveRelease() {
        User buyer = userService.createUser(new CreateUserRequest("DisputeBuyer", UserRole.BUYER, new BigDecimal("500.00")));
        User seller = userService.createUser(new CreateUserRequest("DisputeSeller", UserRole.SELLER, new BigDecimal("0.00")));

        Instant deadline = Instant.now().plus(30, ChronoUnit.MINUTES);
        Transaction tx = transactionService.createTransaction(
                new CreateTransactionRequest(buyer.getId(), seller.getId(), null, new BigDecimal("100.00"), deadline));

        transactionService.fundTransaction(tx.getId(), buyer.getId());
        Dispute dispute = transactionService.fileDispute(tx.getId(), buyer.getId(), "Item not delivered");

        assertThat(dispute.getStatus()).isEqualTo(DisputeStatus.OPEN);
        assertThat(transactionService.getTransactionById(tx.getId()).getStatus()).isEqualTo(TransactionStatus.DISPUTED);

        Dispute resolved = disputeService.resolveDispute(dispute.getId(), DisputeResolution.RELEASE);

        User sellerAfter = userRepository.findById(seller.getId()).orElseThrow();
        assertThat(sellerAfter.getBalance()).isEqualByComparingTo("100.00");

        EscrowAccount escrow = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);

        assertThat(transactionService.getTransactionById(tx.getId()).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(resolved.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(resolved.getResolution()).isEqualTo(DisputeResolution.RELEASE);
    }

    /**
     * Task 13.3: create users → create transaction → fund → dispute → resolve REFUND
     */
    @Test
    @Transactional
    void fundDisputeAndResolveRefund() {
        User buyer = userService.createUser(new CreateUserRequest("RefundBuyer", UserRole.BUYER, new BigDecimal("500.00")));
        User seller = userService.createUser(new CreateUserRequest("RefundSeller", UserRole.SELLER, new BigDecimal("0.00")));

        Instant deadline = Instant.now().plus(30, ChronoUnit.MINUTES);
        Transaction tx = transactionService.createTransaction(
                new CreateTransactionRequest(buyer.getId(), seller.getId(), null, new BigDecimal("150.00"), deadline));

        transactionService.fundTransaction(tx.getId(), buyer.getId());

        BigDecimal buyerAfterFunding = userRepository.findById(buyer.getId()).orElseThrow().getBalance();

        Dispute dispute = transactionService.fileDispute(tx.getId(), seller.getId(), "Buyer not responding");
        Dispute resolved = disputeService.resolveDispute(dispute.getId(), DisputeResolution.REFUND);

        User buyerAfterRefund = userRepository.findById(buyer.getId()).orElseThrow();
        assertThat(buyerAfterRefund.getBalance()).isEqualByComparingTo(buyerAfterFunding.add(new BigDecimal("150.00")));

        EscrowAccount escrow = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);

        assertThat(transactionService.getTransactionById(tx.getId()).getStatus()).isEqualTo(TransactionStatus.REFUNDED);
        assertThat(resolved.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
        assertThat(resolved.getResolution()).isEqualTo(DisputeResolution.REFUND);
    }

    /**
     * Task 13.4: scheduler auto-release flow
     */
    @Test
    @Transactional
    void schedulerAutoRelease() {
        User buyer = userService.createUser(new CreateUserRequest("SchedBuyer", UserRole.BUYER, new BigDecimal("500.00")));
        User seller = userService.createUser(new CreateUserRequest("SchedSeller", UserRole.SELLER, new BigDecimal("0.00")));

        // Create with future deadline, then fund
        Instant deadline = Instant.now().plus(30, ChronoUnit.MINUTES);
        Transaction tx = transactionService.createTransaction(
                new CreateTransactionRequest(buyer.getId(), seller.getId(), null, new BigDecimal("200.00"), deadline));
        transactionService.fundTransaction(tx.getId(), buyer.getId());

        // Manually override deadline to be in the past (simulating overdue)
        Transaction fundedTx = transactionService.getTransactionById(tx.getId());
        fundedTx.setDeadline(Instant.now().minus(5, ChronoUnit.MINUTES));

        // Trigger auto-release directly
        transactionService.autoRelease(fundedTx);

        User sellerAfter = userRepository.findById(seller.getId()).orElseThrow();
        assertThat(sellerAfter.getBalance()).isEqualByComparingTo("200.00");

        Transaction completedTx = transactionService.getTransactionById(tx.getId());
        assertThat(completedTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

        EscrowAccount escrow = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);
    }
}
