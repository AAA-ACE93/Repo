package com.escrow.repository;

import com.escrow.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EscrowAccountRepository escrowAccountRepository;

    @Autowired
    private DisputeRepository disputeRepository;

    private User saveBuyer(String name) {
        return userRepository.save(new User(name, UserRole.BUYER, new BigDecimal("1000.00")));
    }

    private User saveSeller(String name) {
        return userRepository.save(new User(name, UserRole.SELLER, new BigDecimal("0.00")));
    }

    private User saveArbitrator(String name) {
        return userRepository.save(new User(name, UserRole.ARBITRATOR, new BigDecimal("0.00")));
    }

    private Transaction saveTransaction(User buyer, User seller, User arbitrator,
                                         TransactionStatus status, Instant deadline) {
        Transaction tx = new Transaction();
        tx.setBuyer(buyer);
        tx.setSeller(seller);
        tx.setArbitrator(arbitrator);
        tx.setAmount(new BigDecimal("100.00"));
        tx.setStatus(status);
        tx.setDeadline(deadline);
        return transactionRepository.save(tx);
    }

    @Test
    void findByBuyerOrSellerOrArbitrator_returnsCorrectSubset() {
        User buyer = saveBuyer("Alice");
        User seller = saveSeller("Bob");
        User arbitrator = saveArbitrator("Charlie");
        User other = userRepository.save(new User("Dave", UserRole.BUYER, BigDecimal.ZERO));

        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        Transaction tx1 = saveTransaction(buyer, seller, null, TransactionStatus.PENDING, future);
        Transaction tx2 = saveTransaction(other, seller, arbitrator, TransactionStatus.PENDING, future);
        Transaction tx3 = saveTransaction(other, userRepository.save(new User("Eve", UserRole.SELLER, BigDecimal.ZERO)), null, TransactionStatus.PENDING, future);

        List<Transaction> forBuyer = transactionRepository.findByBuyerIdOrSellerIdOrArbitratorId(buyer.getId(), buyer.getId(), buyer.getId());
        assertThat(forBuyer).containsExactly(tx1);

        List<Transaction> forSeller = transactionRepository.findByBuyerIdOrSellerIdOrArbitratorId(seller.getId(), seller.getId(), seller.getId());
        assertThat(forSeller).containsExactlyInAnyOrder(tx1, tx2);

        List<Transaction> forArbitrator = transactionRepository.findByBuyerIdOrSellerIdOrArbitratorId(arbitrator.getId(), arbitrator.getId(), arbitrator.getId());
        assertThat(forArbitrator).containsExactly(tx2);

        List<Transaction> forOther = transactionRepository.findByBuyerIdOrSellerIdOrArbitratorId(other.getId(), other.getId(), other.getId());
        assertThat(forOther).containsExactlyInAnyOrder(tx2, tx3);
    }

    @Test
    void findByStatusAndDeadlineBefore_returnsOnlyFundedOverdue() {
        User buyer = saveBuyer("Buyer1");
        User seller = saveSeller("Seller1");

        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        Transaction funded_past = saveTransaction(buyer, seller, null, TransactionStatus.FUNDED, past);
        saveTransaction(buyer, seller, null, TransactionStatus.FUNDED, future); // not overdue
        saveTransaction(buyer, seller, null, TransactionStatus.PENDING, past); // wrong status
        saveTransaction(buyer, seller, null, TransactionStatus.COMPLETED, past); // wrong status

        List<Transaction> results = transactionRepository.findByStatusAndDeadlineBefore(TransactionStatus.FUNDED, Instant.now());
        assertThat(results).containsExactly(funded_past);
    }

    @Test
    void existsByTransactionIdAndStatusIn_returnsCorrectly() {
        User buyer = saveBuyer("DisputeBuyer");
        User seller = saveSeller("DisputeSeller");

        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        Transaction tx = saveTransaction(buyer, seller, null, TransactionStatus.DISPUTED, future);

        Dispute dispute = new Dispute();
        dispute.setTransaction(tx);
        dispute.setRaisedBy(buyer);
        dispute.setReason("Test reason");
        dispute.setStatus(DisputeStatus.OPEN);
        disputeRepository.save(dispute);

        boolean exists = disputeRepository.existsByTransactionIdAndStatusIn(
                tx.getId(), List.of(DisputeStatus.OPEN, DisputeStatus.IN_PROGRESS));
        assertThat(exists).isTrue();

        boolean notExists = disputeRepository.existsByTransactionIdAndStatusIn(
                tx.getId(), List.of(DisputeStatus.RESOLVED));
        assertThat(notExists).isFalse();
    }

    @Test
    void findByTransactionId_returnsEscrowForTransaction() {
        User buyer = saveBuyer("EscrowBuyer");
        User seller = saveSeller("EscrowSeller");

        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        Transaction tx = saveTransaction(buyer, seller, null, TransactionStatus.FUNDED, future);

        EscrowAccount escrow = new EscrowAccount(tx, new BigDecimal("100.00"), EscrowStatus.LOCKED);
        escrowAccountRepository.save(escrow);

        Optional<EscrowAccount> found = escrowAccountRepository.findByTransactionId(tx.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getLockedAmount()).isEqualByComparingTo("100.00");
        assertThat(found.get().getStatus()).isEqualTo(EscrowStatus.LOCKED);
    }
}
