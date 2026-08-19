package com.escrow.property;

import com.escrow.dto.CreateTransactionRequest;
import com.escrow.dto.CreateUserRequest;
import com.escrow.exception.InsufficientFundsException;
import com.escrow.model.*;
import com.escrow.repository.EscrowAccountRepository;
import com.escrow.repository.UserRepository;
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
 * Property 6: Funding Produces Correct State Changes — Validates: Requirements 3.1
 * Property 7: Insufficient Funds Leaves All State Unchanged — Validates: Requirements 3.2
 */
@SpringBootTest
@ActiveProfiles("test")
class FundingPropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private EscrowAccountRepository escrowAccountRepository;

    @Autowired
    private UserRepository userRepository;

    private static final Random RNG = new Random(456L);

    /**
     * Property 6: Funding Produces Correct State Changes
     * Validates: Requirements 3.1
     */
    @Test
    @Transactional
    void fundingProducesCorrectStateChanges() {
        for (int i = 0; i < 50; i++) {
            BigDecimal amt = randomAmount(1.00, 1000.00);
            BigDecimal buyerInitialBalance = amt.add(new BigDecimal("500.00"));

            User buyer = userService.createUser(new CreateUserRequest("BuyerP6_" + i, UserRole.BUYER, buyerInitialBalance));
            User seller = userService.createUser(new CreateUserRequest("SellerP6_" + i, UserRole.SELLER, BigDecimal.ZERO));

            Instant deadline = Instant.now().plus(10 + i, ChronoUnit.MINUTES);
            Transaction tx = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt, deadline));

            transactionService.fundTransaction(tx.getId(), buyer.getId());

            User updatedBuyer = userRepository.findById(buyer.getId()).orElseThrow();
            assertThat(updatedBuyer.getBalance())
                    .as("buyer balance after fund").isEqualByComparingTo(buyerInitialBalance.subtract(amt));

            Transaction updatedTx = transactionService.getTransactionById(tx.getId());
            assertThat(updatedTx.getStatus()).isEqualTo(TransactionStatus.FUNDED);

            EscrowAccount escrow = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();
            assertThat(escrow.getLockedAmount()).isEqualByComparingTo(amt);
            assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.LOCKED);
        }
    }

    /**
     * Property 7: Insufficient Funds Leaves All State Unchanged
     * Validates: Requirements 3.2
     */
    @Test
    @Transactional
    void insufficientFundsLeavesStateUnchanged() {
        for (int i = 0; i < 50; i++) {
            BigDecimal amt = randomAmount(1.01, 1000.00);
            BigDecimal buyerBalance = amt.subtract(new BigDecimal("0.01"));
            if (buyerBalance.compareTo(BigDecimal.ZERO) < 0) buyerBalance = BigDecimal.ZERO;

            User buyer = userService.createUser(new CreateUserRequest("BuyerP7_" + i, UserRole.BUYER, buyerBalance));
            User seller = userService.createUser(new CreateUserRequest("SellerP7_" + i, UserRole.SELLER, BigDecimal.ZERO));

            Instant deadline = Instant.now().plus(10 + i, ChronoUnit.MINUTES);
            Transaction tx = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt, deadline));

            final BigDecimal expectedBalance = buyerBalance;
            final Long txId = tx.getId();
            final Long buyerId = buyer.getId();

            assertThatThrownBy(() -> transactionService.fundTransaction(txId, buyerId))
                    .isInstanceOf(InsufficientFundsException.class);

            User unchanged = userRepository.findById(buyerId).orElseThrow();
            assertThat(unchanged.getBalance()).isEqualByComparingTo(expectedBalance);

            Transaction unchangedTx = transactionService.getTransactionById(txId);
            assertThat(unchangedTx.getStatus()).isEqualTo(TransactionStatus.PENDING);
            assertThat(escrowAccountRepository.findByTransactionId(txId)).isEmpty();
        }
    }

    private BigDecimal randomAmount(double min, double max) {
        double raw = min + RNG.nextDouble() * (max - min);
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
