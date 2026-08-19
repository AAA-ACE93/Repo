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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 11: Dispute Filing Produces Correct Initial State
 * Validates: Requirements 6.1
 */
@SpringBootTest
@ActiveProfiles("test")
class DisputePropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    private static final Random RNG = new Random(321L);

    /**
     * Property 11: Dispute Filing Produces Correct Initial State
     * Validates: Requirements 6.1
     *
     * For any FUNDED tx before deadline, raiser is buyer or seller:
     * (a) dispute created with status OPEN, (b) transaction status DISPUTED.
     */
    @Test
    @Transactional
    void disputeFilingProducesCorrectInitialState() {
        for (int i = 0; i < 50; i++) {
            boolean buyerRaises = RNG.nextBoolean();

            User buyer = userService.createUser(new CreateUserRequest("BuyerP11_" + i, UserRole.BUYER, new BigDecimal("1000.00")));
            User seller = userService.createUser(new CreateUserRequest("SellerP11_" + i, UserRole.SELLER, BigDecimal.ZERO));

            Instant deadline = Instant.now().plus(30 + i, ChronoUnit.MINUTES);
            Transaction tx = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, new BigDecimal("100.00"), deadline));

            transactionService.fundTransaction(tx.getId(), buyer.getId());

            Long raisedBy = buyerRaises ? buyer.getId() : seller.getId();
            String reason = "Dispute reason " + i;
            Dispute dispute = transactionService.fileDispute(tx.getId(), raisedBy, reason);

            assertThat(dispute.getStatus()).as("dispute status at iteration %d", i).isEqualTo(DisputeStatus.OPEN);

            Transaction updatedTx = transactionService.getTransactionById(tx.getId());
            assertThat(updatedTx.getStatus()).as("tx status at iteration %d", i).isEqualTo(TransactionStatus.DISPUTED);
        }
    }
}
