package com.escrow.repository;

import com.escrow.domain.entity.LedgerAccount;
import com.escrow.domain.enums.LedgerAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByAccountNumber(String accountNumber);
    Optional<LedgerAccount> findByEscrowIdAndAccountType(UUID escrowId, LedgerAccountType accountType);
    Optional<LedgerAccount> findByUserIdAndAccountType(UUID userId, LedgerAccountType accountType);
}
