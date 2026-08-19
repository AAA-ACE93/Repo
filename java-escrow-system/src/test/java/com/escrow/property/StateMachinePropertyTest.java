package com.escrow.property;

import com.escrow.dto.CreateTransactionRequest;
import com.escrow.dto.CreateUserRequest;
import com.escrow.exception.*;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property 14: Valid State Machine Transitions Only
 * Validates: Requirements 9.1, 9.2
 */
@SpringBootTest
@ActiveProfiles("test")
class StateMachinePropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    private static final Random RNG = new Random(111L);

    /**
     * Property 14: Valid State Machine Transitions Only
     * Validates: Requirements 9.1, 9.2
     *
     * Enumerates all disallowed (status, operation) pairs and verifies each is rejected.
     */
    @Test
    @Transactional
    void pendingCannotBeConfirmed() {
        for (int i = 0; i < 10; i++) {
            var ids = createBuyerSeller(i);
            Transaction tx = createPendingTx(ids[0], ids[1], randomAmount());
            assertThatThrownBy(() -> transactionService.confirmTransaction(tx.getId(), ids[0]))
                    .isInstanceOf(InvalidTransactionStatusException.class);
        }
    }

    @Test
    @Transactional
    void pendingCannotBeDisputed() {
        for (int i = 0; i < 10; i++) {
            var ids = createBuyerSeller(100 + i);
            Transaction tx = createPendingTx(ids[0], ids[1], randomAmount());
            assertThatThrownBy(() -> transactionService.fileDispute(tx.getId(), ids[0], "reason"))
                    .isInstanceOf(InvalidTransactionStatusException.class);
        }
    }

    @Test
    @Transactional
    void fundedCannotBeFundedAgain() {
        for (int i = 0; i < 10; i++) {
            BigDecimal amt = randomAmount();
            var ids = createBuyerSeller(200 + i);
            Transaction tx = createPendingTx(ids[0], ids[1], amt);
            transactionService.fundTransaction(tx.getId(), ids[0]);
            assertThatThrownBy(() -> transactionService.fundTransaction(tx.getId(), ids[0]))
                    .isInstanceOf(InvalidTransactionStatusException.class);
        }
    }

    @Test
    @Transactional
    void completedCannotBeFunded() {
        for (int i = 0; i < 10; i++) {
            BigDecimal amt = randomAmount();
            var ids = createBuyerSeller(300 + i);
            Transaction tx = createPendingTx(ids[0], ids[1], amt);
            transactionService.fundTransaction(tx.getId(), ids[0]);
            transactionService.confirmTransaction(tx.getId(), ids[0]);
            assertThatThrownBy(() -> transactionService.fundTransaction(tx.getId(), ids[0]))
                    .isInstanceOf(InvalidTransactionStatusException.class);
        }
    }

    @Test
    @Transactional
    void completedCannotBeConfirmedAgain() {
        for (int i = 0; i < 10; i++) {
            BigDecimal amt = randomAmount();
            var ids = createBuyerSeller(400 + i);
            Transaction tx = createPendingTx(ids[0], ids[1], amt);
            transactionService.fundTransaction(tx.getId(), ids[0]);
            transactionService.confirmTransaction(tx.getId(), ids[0]);
            assertThatThrownBy(() -> transactionService.confirmTransaction(tx.getId(), ids[0]))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    @Transactional
    void disputedCannotBeConfirmedDirectly() {
        for (int i = 0; i < 10; i++) {
            BigDecimal amt = randomAmount();
            var ids = createBuyerSeller(500 + i);
            Transaction tx = createPendingTx(ids[0], ids[1], amt);
            transactionService.fundTransaction(tx.getId(), ids[0]);
            transactionService.fileDispute(tx.getId(), ids[0], "reason");
            assertThatThrownBy(() -> transactionService.confirmTransaction(tx.getId(), ids[0]))
                    .isInstanceOf(ConflictException.class);
        }
    }

    // ---- Helpers ----

    private Long[] createBuyerSeller(int suffix) {
        User buyer = userService.createUser(new CreateUserRequest("SMBuyer_" + suffix, UserRole.BUYER, new BigDecimal("99999.99")));
        User seller = userService.createUser(new CreateUserRequest("SMSeller_" + suffix, UserRole.SELLER, BigDecimal.ZERO));
        return new Long[]{buyer.getId(), seller.getId()};
    }

    private Transaction createPendingTx(Long buyerId, Long sellerId, BigDecimal amount) {
        Instant deadline = Instant.now().plus(30, ChronoUnit.MINUTES);
        return transactionService.createTransaction(
                new CreateTransactionRequest(buyerId, sellerId, null, amount, deadline));
    }

    private BigDecimal randomAmount() {
        double raw = 1.00 + RNG.nextDouble() * 999.00;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
