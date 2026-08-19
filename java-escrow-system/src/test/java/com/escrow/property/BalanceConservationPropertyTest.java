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
 * Property 15: Balance Conservation Invariant
 * Validates: Requirements 9.3
 */
@SpringBootTest
@ActiveProfiles("test")
class BalanceConservationPropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private DisputeService disputeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EscrowAccountRepository escrowAccountRepository;

    private static final Random RNG = new Random(333L);

    /**
     * Property 15: Balance Conservation Invariant
     * Validates: Requirements 9.3
     *
     * For any sequence of operations, sum(balances) + sum(locked_escrow) == initial_total.
     */
    @Test
    @Transactional
    void balanceConservationInvariant() {
        for (int i = 0; i < 50; i++) {
            int scenarioType = RNG.nextInt(3);
            BigDecimal amt = randomAmount(10.00, 1000.00);

            User buyer = userService.createUser(new CreateUserRequest("ConsBuyer_" + i, UserRole.BUYER, amt.add(new BigDecimal("500.00"))));
            User seller = userService.createUser(new CreateUserRequest("ConsSeller_" + i, UserRole.SELLER, new BigDecimal("200.00")));

            BigDecimal initialTotal = buyer.getBalance().add(seller.getBalance());

            assertConservation(buyer.getId(), seller.getId(), initialTotal);

            Instant deadline = Instant.now().plus(30 + i, ChronoUnit.MINUTES);
            Transaction tx = transactionService.createTransaction(
                    new CreateTransactionRequest(buyer.getId(), seller.getId(), null, amt, deadline));

            assertConservation(buyer.getId(), seller.getId(), initialTotal);

            transactionService.fundTransaction(tx.getId(), buyer.getId());
            assertConservation(buyer.getId(), seller.getId(), initialTotal);

            if (scenarioType == 0) {
                // Confirm → seller gets funds
                transactionService.confirmTransaction(tx.getId(), buyer.getId());
                assertConservation(buyer.getId(), seller.getId(), initialTotal);
            } else if (scenarioType == 1) {
                // Dispute → RELEASE
                Dispute dispute = transactionService.fileDispute(tx.getId(), buyer.getId(), "test " + i);
                assertConservation(buyer.getId(), seller.getId(), initialTotal);
                disputeService.resolveDispute(dispute.getId(), DisputeResolution.RELEASE);
                assertConservation(buyer.getId(), seller.getId(), initialTotal);
            } else {
                // Dispute → REFUND
                Dispute dispute = transactionService.fileDispute(tx.getId(), buyer.getId(), "test " + i);
                assertConservation(buyer.getId(), seller.getId(), initialTotal);
                disputeService.resolveDispute(dispute.getId(), DisputeResolution.REFUND);
                assertConservation(buyer.getId(), seller.getId(), initialTotal);
            }
        }
    }

    private void assertConservation(Long buyerId, Long sellerId, BigDecimal initialTotal) {
        BigDecimal buyerBalance = userRepository.findById(buyerId).orElseThrow().getBalance();
        BigDecimal sellerBalance = userRepository.findById(sellerId).orElseThrow().getBalance();

        BigDecimal lockedEscrow = escrowAccountRepository.findAll().stream()
                .filter(e -> e.getStatus() == EscrowStatus.LOCKED)
                .filter(e -> {
                    Transaction t = e.getTransaction();
                    return t.getBuyer().getId().equals(buyerId) || t.getSeller().getId().equals(sellerId);
                })
                .map(EscrowAccount::getLockedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = buyerBalance.add(sellerBalance).add(lockedEscrow);
        assertThat(total)
                .as("Conservation: expected %s, got buyer=%s + seller=%s + locked=%s = %s",
                        initialTotal, buyerBalance, sellerBalance, lockedEscrow, total)
                .isEqualByComparingTo(initialTotal);
    }

    private BigDecimal randomAmount(double min, double max) {
        double raw = min + RNG.nextDouble() * (max - min);
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }
}
