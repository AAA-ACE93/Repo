package com.escrow.repository;

import com.escrow.domain.entity.LedgerTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByTransactionReference(String transactionReference);
    List<LedgerTransaction> findByEscrowId(UUID escrowId);
    Page<LedgerTransaction> findByEscrowId(UUID escrowId, Pageable pageable);
}
