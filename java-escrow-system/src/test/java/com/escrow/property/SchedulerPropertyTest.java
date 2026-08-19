package com.escrow.property;

import com.escrow.dto.CreateTransactionRequest;
import com.escrow.dto.CreateUserRequest;
import com.escrow.model.*;
import com.escrow.repository.EscrowAccountRepository;
import com.escrow.repository.UserRepository;
import com.escrow.scheduler.SchedulerService;
import com.escrow.service.TransactionService;
import com.escrow.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 9: Scheduler Eligibility Correctness — Validates: Requirements 5.1, 6.7
 * Property 10: Scheduler Auto-Release Idempotence — Validates: Requirements 5.6
 */
@SpringBootTest
@ActiveProfiles("test")
class SchedulerPropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private EscrowAccountRepository escrowAccountRepository;

    @Autowired
    private UserRepository userRepository;

    private static final Random RNG = new Random(987L);
    private static final TransactionStatus[] ALL_STATUSES = TransactionStatus.values();

    /**
     * Property 9: Scheduler Eligibility Correctness
     * Validates: Requirements 5.1, 6.7
     *
     * Tests the pure isEligible() method with all combinations of status/deadline/dispute.
     */
    @Test
    void schedulerEligibilityCorrectness() {
        User buyer = new User("b", UserRole.BUYER, BigDecimal.ZERO);
        User seller = new User("s", UserRole.SELLER, BigDecimal.ZERO);

        // Test all combinations of (status, isOverdue, hasActiveDispute) — 5 * 2 * 2 = 20
        for (TransactionStatus status : ALL_STATUSES) {
            for (boolean isOverdue : new boolean[]{true, false}) {
                for (boolean hasActiveDispute : new boolean[]{true, false}) {
                    Transaction tx = new Transaction();
                    tx.setBuyer(buyer);
                    tx.setSeller(seller);
                    tx.setAmount(new BigDecimal("100.00"));
                    tx.setStatus(status);
                    tx.setDeadline(isOverdue
                            ? Instant.now().minus(1, ChronoUnit.HOURS)
                            : Instant.now().plus(1, ChronoUnit.HOURS));

                    boolean eligible = SchedulerService.isEligible(tx, hasActiveDispute);
                    boolean expected = status == TransactionStatus.FUNDED && isOverdue && !hasActiveDispute;

                    assertThat(eligible)
                            .as("status=%s, overdue=%s, dispute=%s → eligible should be %s",
                                    status, isOverdue, hasActiveDispute, expected)
                            .isEqualTo(expected);
                }
            }
        }
    }

    /**
     * Property 10: Scheduler Auto-Release Idempotence
     * Validates: Requirements 5.6
     *
     * Calling autoRelease twice on an already-COMPLETED transaction is a no-op.
     */
    @Test
    @Transactional
    void schedulerAutoReleaseIdempotence() {
        for (int i = 0; i < 30; i++) {
            BigDecimal amt = randomAmount(1.00, 500.00);
            User buyer = userService.createUser(new CreateUserRequest("BuyerP10_" + i, UserRole.BUYER, amt.add(new BigDecimal("500.00"))));
            User seller = userService.createUser(new CreateUserRequest("SellerP10_" + i, UserRole.SELLER, BigDecimal.ZERO));

            Transaction tx = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt,
                            Instant.now().plus(10, ChronoUnit.MINUTES)));
            transactionService.fundTransaction(tx.getId(), buyer.getId());

            // Set deadline to past in the entity (within same transaction)
            Transaction fundedTx = transactionService.getTransactionById(tx.getId());
            fundedTx.setDeadline(Instant.now().minus(5, ChronoUnit.MINUTES));

            // First auto-release
            transactionService.autoRelease(fundedTx);

            BigDecimal sellerAfterFirst = userRepository.findById(seller.getId()).orElseThrow().getBalance();
            EscrowAccount escrowAfterFirst = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();

            assertThat(transactionService.getTransactionById(tx.getId()).getStatus())
                    .isEqualTo(TransactionStatus.COMPLETED);

            // Second auto-release — should be a no-op
            transactionService.autoRelease(fundedTx);

            BigDecimal sellerAfterSecond = userRepository.findById(seller.getId()).orElseThrow().getBalance();
            EscrowAccount escrowAfterSecond = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();

            assertThat(sellerAfterSecond).as("seller balance unchanged after 2nd release").isEqualByComparingTo(sellerAfterFirst);
            assertThat(escrowAfterSecond.getStatus()).isEqualTo(EscrowStatus.RELEASED);
            assertThat(transactionService.getTransactionById(tx.getId()).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }
    }

    private BigDecimal randomAmount(double min, double max) {
        double raw = min + RNG.nextDouble() * (max - min);
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
