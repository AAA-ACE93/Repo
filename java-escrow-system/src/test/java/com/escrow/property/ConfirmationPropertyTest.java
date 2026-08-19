package com.escrow.property;

import com.escrow.dto.CreateTransactionRequest;
import com.escrow.dto.CreateUserRequest;
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

/**
 * Property 8: Buyer Confirmation Transfers Escrow to Seller
 * Validates: Requirements 4.1, 9.6
 */
@SpringBootTest
@ActiveProfiles("test")
class ConfirmationPropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private EscrowAccountRepository escrowAccountRepository;

    @Autowired
    private UserRepository userRepository;

    private static final Random RNG = new Random(789L);

    /**
     * Property 8: Buyer Confirmation Transfers Escrow to Seller
     * Validates: Requirements 4.1, 9.6
     *
     * For any FUNDED tx with LOCKED escrow amount A, after Buyer confirms:
     * (a) seller balance += A, (b) escrow RELEASED, (c) tx COMPLETED.
     */
    @Test
    @Transactional
    void buyerConfirmationTransfersEscrowToSeller() {
        for (int i = 0; i < 50; i++) {
            BigDecimal amt = randomAmount(1.00, 1000.00);

            User buyer = userService.createUser(new CreateUserRequest("BuyerP8_" + i, UserRole.BUYER, amt.add(new BigDecimal("500.00"))));
            User seller = userService.createUser(new CreateUserRequest("SellerP8_" + i, UserRole.SELLER, BigDecimal.ZERO));

            Instant deadline = Instant.now().plus(10 + i, ChronoUnit.MINUTES);
            Transaction tx = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt, deadline));

            transactionService.fundTransaction(tx.getId(), buyer.getId());

            BigDecimal sellerBefore = userRepository.findById(seller.getId()).orElseThrow().getBalance();

            transactionService.confirmTransaction(tx.getId(), buyer.getId());

            User updatedSeller = userRepository.findById(seller.getId()).orElseThrow();
            assertThat(updatedSeller.getBalance())
                    .as("seller balance after confirm").isEqualByComparingTo(sellerBefore.add(amt));

            EscrowAccount escrow = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();
            assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);

            Transaction updatedTx = transactionService.getTransactionById(tx.getId());
            assertThat(updatedTx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        }
    }

    private BigDecimal randomAmount(double min, double max) {
        double raw = min + RNG.nextDouble() * (max - min);
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
