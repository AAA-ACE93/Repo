package com.escrow.repository;

import com.escrow.model.Transaction;
import com.escrow.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByBuyerIdOrSellerIdOrArbitratorId(Long buyerId, Long sellerId, Long arbitratorId);

    List<Transaction> findByStatusAndDeadlineBefore(TransactionStatus status, Instant deadline);
}
