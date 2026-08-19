package com.escrow.property;

import com.escrow.dto.CreateTransactionRequest;
import com.escrow.dto.CreateUserRequest;
import com.escrow.exception.ConflictException;
import com.escrow.exception.InvalidTransactionStatusException;
import com.escrow.model.*;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property 16: Terminal State Immutability
 * Validates: Requirements 9.4
 */
@SpringBootTest
@ActiveProfiles("test")
class TerminalStatePropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    private static final Random RNG = new Random(222L);

    /**
     * Property 16a: COMPLETED terminal - fund rejected
     * Validates: Requirements 9.4
     */
    @Test
    @Transactional
    void completedTerminalFundRejected() {
        for (int i = 0; i < 20; i++) {
            Transaction tx = buildCompletedTransaction(i);
            assertThatThrownBy(() -> transactionService.fundTransaction(tx.getId(), tx.getBuyer().getId()))
                    .isInstanceOf(InvalidTransactionStatusException.class);
            assertThat(transactionService.getTransactionById(tx.getId()).getStatus())
                    .isEqualTo(TransactionStatus.COMPLETED);
        }
    }

    /**
     * Property 16b: COMPLETED terminal - confirm rejected
     * Validates: Requirements 9.4
     */
    @Test
    @Transactional
    void completedTerminalConfirmRejected() {
        for (int i = 0; i < 20; i++) {
            Transaction tx = buildCompletedTransaction(100 + i);
            assertThatThrownBy(() -> transactionService.confirmTransaction(tx.getId(), tx.getBuyer().getId()))
                    .isInstanceOf(ConflictException.class);
            assertThat(transactionService.getTransactionById(tx.getId()).getStatus())
                    .isEqualTo(TransactionStatus.COMPLETED);
        }
    }

    /**
     * Property 16c: COMPLETED terminal - dispute rejected
     * Validates: Requirements 9.4
     */
    @Test
    @Transactional
    void completedTerminalDisputeRejected() {
        for (int i = 0; i < 20; i++) {
            Transaction tx = buildCompletedTransaction(200 + i);
            assertThatThrownBy(() -> transactionService.fileDispute(tx.getId(), tx.getBuyer().getId(), "reason"))
                    .isInstanceOf(InvalidTransactionStatusException.class);
            assertThat(transactionService.getTransactionById(tx.getId()).getStatus())
                    .isEqualTo(TransactionStatus.COMPLETED);
        }
    }

    // ---- Helper ----

    private Transaction buildCompletedTransaction(int suffix) {
        BigDecimal amt = randomAmount();
        User buyer = userService.createUser(new CreateUserRequest("TBuyer_" + suffix, UserRole.BUYER, amt.add(new BigDecimal("1000.00"))));
        User seller = userService.createUser(new CreateUserRequest("TSeller_" + suffix, UserRole.SELLER, BigDecimal.ZERO));
        Instant deadline = Instant.now().plus(30, ChronoUnit.MINUTES);
        Transaction tx = transactionService.createTransaction(
                new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt, deadline));
        transactionService.fundTransaction(tx.getId(), buyer.getId());
        transactionService.confirmTransaction(tx.getId(), buyer.getId());
        return transactionService.getTransactionById(tx.getId());
    }

    private BigDecimal randomAmount() {
        double raw = 1.00 + RNG.nextDouble() * 99.00;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
