package com.escrow.property;

import com.escrow.dto.CreateTransactionRequest;
import com.escrow.dto.CreateUserRequest;
import com.escrow.model.*;
import com.escrow.repository.EscrowAccountRepository;
import com.escrow.repository.UserRepository;
import com.escrow.service.DisputeService;
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
 * Property 12: RELEASE Resolution Credits Seller — Validates: Requirements 7.1
 * Property 13: REFUND Resolution Credits Buyer — Validates: Requirements 7.2
 */
@SpringBootTest
@ActiveProfiles("test")
class DisputeResolutionPropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private DisputeService disputeService;

    @Autowired
    private EscrowAccountRepository escrowAccountRepository;

    @Autowired
    private UserRepository userRepository;

    private static final Random RNG = new Random(654L);

    /**
     * Property 12: RELEASE Resolution Credits Seller
     * Validates: Requirements 7.1
     */
    @Test
    @Transactional
    void releaseResolutionCreditsSeller() {
        for (int i = 0; i < 50; i++) {
            BigDecimal amt = randomAmount(1.00, 1000.00);

            User buyer = userService.createUser(new CreateUserRequest("BuyerP12_" + i, UserRole.BUYER, amt.add(new BigDecimal("500.00"))));
            User seller = userService.createUser(new CreateUserRequest("SellerP12_" + i, UserRole.SELLER, BigDecimal.ZERO));

            Instant deadline = Instant.now().plus(30 + i, ChronoUnit.MINUTES);
            Transaction tx = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt, deadline));
            transactionService.fundTransaction(tx.getId(), buyer.getId());
            Dispute dispute = transactionService.fileDispute(tx.getId(), buyer.getId(), "reason " + i);

            BigDecimal sellerBefore = userRepository.findById(seller.getId()).orElseThrow().getBalance();

            Dispute resolved = disputeService.resolveDispute(dispute.getId(), DisputeResolution.RELEASE);

            User updatedSeller = userRepository.findById(seller.getId()).orElseThrow();
            assertThat(updatedSeller.getBalance()).as("seller balance").isEqualByComparingTo(sellerBefore.add(amt));

            EscrowAccount escrow = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();
            assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);

            assertThat(transactionService.getTransactionById(tx.getId()).getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(resolved.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
            assertThat(resolved.getResolution()).isEqualTo(DisputeResolution.RELEASE);
        }
    }

    /**
     * Property 13: REFUND Resolution Credits Buyer
     * Validates: Requirements 7.2
     */
    @Test
    @Transactional
    void refundResolutionCreditsBuyer() {
        for (int i = 0; i < 50; i++) {
            BigDecimal amt = randomAmount(1.00, 1000.00);

            User buyer = userService.createUser(new CreateUserRequest("BuyerP13_" + i, UserRole.BUYER, amt.add(new BigDecimal("500.00"))));
            User seller = userService.createUser(new CreateUserRequest("SellerP13_" + i, UserRole.SELLER, BigDecimal.ZERO));

            Instant deadline = Instant.now().plus(30 + i, ChronoUnit.MINUTES);
            Transaction tx = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt, deadline));
            transactionService.fundTransaction(tx.getId(), buyer.getId());

            BigDecimal buyerAfterFunding = userRepository.findById(buyer.getId()).orElseThrow().getBalance();

            Dispute dispute = transactionService.fileDispute(tx.getId(), buyer.getId(), "reason " + i);
            Dispute resolved = disputeService.resolveDispute(dispute.getId(), DisputeResolution.REFUND);

            User updatedBuyer = userRepository.findById(buyer.getId()).orElseThrow();
            assertThat(updatedBuyer.getBalance()).as("buyer balance").isEqualByComparingTo(buyerAfterFunding.add(amt));

            EscrowAccount escrow = escrowAccountRepository.findByTransactionId(tx.getId()).orElseThrow();
            assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);

            assertThat(transactionService.getTransactionById(tx.getId()).getStatus()).isEqualTo(TransactionStatus.REFUNDED);
            assertThat(resolved.getStatus()).isEqualTo(DisputeStatus.RESOLVED);
            assertThat(resolved.getResolution()).isEqualTo(DisputeResolution.REFUND);
        }
    }

    private BigDecimal randomAmount(double min, double max) {
        double raw = min + RNG.nextDouble() * (max - min);
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
