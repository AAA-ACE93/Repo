package com.escrow.repository;

import com.escrow.model.EscrowAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, Long> {

    Optional<EscrowAccount> findByTransactionId(Long transactionId);
}
