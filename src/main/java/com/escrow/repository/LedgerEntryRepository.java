package com.escrow.repository;

import com.escrow.domain.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByLedgerAccountId(UUID ledgerAccountId);

    @Query("SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0) FROM LedgerEntry e WHERE e.ledgerAccount.id = :accountId")
    BigDecimal calculateBalanceForAccount(@Param("accountId") UUID accountId);
}
