package com.escrow.property;

import com.escrow.dto.CreateTransactionRequest;
import com.escrow.dto.CreateUserRequest;
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

/**
 * Property 3: New Transactions Always Start as PENDING — Validates: Requirements 2.1, 9.5
 * Property 4: Transaction Lookup Round-Trip — Validates: Requirements 2.10
 * Property 5: User Transaction Filter Completeness — Validates: Requirements 2.12
 */
@SpringBootTest
@ActiveProfiles("test")
class TransactionPropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    private static final Random RNG = new Random(123L);

    /**
     * Property 3: New Transactions Always Start as PENDING
     * Validates: Requirements 2.1, 9.5
     */
    @Test
    @Transactional
    void newTransactionsAlwaysStartPending() {
        for (int i = 0; i < 50; i++) {
            BigDecimal amt = randomAmount();
            User buyer = userService.createUser(new CreateUserRequest("BuyerP3_" + i, UserRole.BUYER, new BigDecimal("999999.99")));
            User seller = userService.createUser(new CreateUserRequest("SellerP3_" + i, UserRole.SELLER, BigDecimal.ZERO));

            Instant deadline = Instant.now().plus(10 + i, ChronoUnit.MINUTES);
            Transaction tx = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt, deadline));

            assertThat(tx.getStatus()).as("tx %d should be PENDING", i).isEqualTo(TransactionStatus.PENDING);
            assertThat(tx.getAmount()).as("tx %d amount", i).isEqualByComparingTo(amt);
        }
    }

    /**
     * Property 4: Transaction Lookup Round-Trip
     * Validates: Requirements 2.10
     */
    @Test
    @Transactional
    void transactionLookupRoundTrip() {
        for (int i = 0; i < 50; i++) {
            BigDecimal amt = randomAmount();
            User buyer = userService.createUser(new CreateUserRequest("BuyerP4_" + i, UserRole.BUYER, new BigDecimal("999999.99")));
            User seller = userService.createUser(new CreateUserRequest("SellerP4_" + i, UserRole.SELLER, BigDecimal.ZERO));

            Instant deadline = Instant.now().plus(10 + i, ChronoUnit.MINUTES);
            Transaction created = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt, deadline));

            Transaction fetched = transactionService.getTransactionById(created.getId());

            assertThat(fetched.getBuyer().getId()).isEqualTo(buyer.getId());
            assertThat(fetched.getSeller().getId()).isEqualTo(seller.getId());
            assertThat(fetched.getAmount()).isEqualByComparingTo(amt);
            assertThat(fetched.getDeadline()).isEqualTo(deadline);
            assertThat(fetched.getStatus()).isEqualTo(TransactionStatus.PENDING);
        }
    }

    /**
     * Property 5: User Transaction Filter Completeness
     * Validates: Requirements 2.12
     */
    @Test
    @Transactional
    void userTransactionFilterCompleteness() {
        for (int txCount = 1; txCount <= 5; txCount++) {
            User buyer = userService.createUser(new CreateUserRequest("FilterBuyer_" + txCount, UserRole.BUYER, new BigDecimal("999999.99")));
            User seller = userService.createUser(new CreateUserRequest("FilterSeller_" + txCount, UserRole.SELLER, BigDecimal.ZERO));
            User otherBuyer = userService.createUser(new CreateUserRequest("OtherBuyer_" + txCount, UserRole.BUYER, new BigDecimal("999999.99")));
            User otherSeller = userService.createUser(new CreateUserRequest("OtherSeller_" + txCount, UserRole.SELLER, BigDecimal.ZERO));

            for (int i = 0; i < txCount; i++) {
                Instant deadline = Instant.now().plus(10 + i + txCount * 10L, ChronoUnit.MINUTES);
                transactionService.createTransaction(
                        new CreateTransactionRequest(buyer.getId(), seller.getId(), null, new BigDecimal("10.00"), deadline));
            }
            for (int i = 0; i < 2; i++) {
                Instant deadline = Instant.now().plus(20 + i + txCount * 10L, ChronoUnit.MINUTES);
                transactionService.createTransaction(
                        new CreateTransactionRequest(otherBuyer.getId(), otherSeller.getId(), null, new BigDecimal("5.00"), deadline));
            }

            var results = transactionService.getTransactionsByUserId(buyer.getId());
            assertThat(results).as("filter for txCount=%d", txCount).hasSize(txCount);
            assertThat(results).allSatisfy(tx ->
                    assertThat(tx.getBuyer().getId()).isEqualTo(buyer.getId()));
        }
    }

    private BigDecimal randomAmount() {
        double raw = 0.01 + RNG.nextDouble() * 9999.98;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
