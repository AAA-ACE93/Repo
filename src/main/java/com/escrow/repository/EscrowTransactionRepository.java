package com.escrow.repository;

import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.domain.enums.EscrowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscrowTransactionRepository extends JpaRepository<EscrowTransaction, UUID> {

    Optional<EscrowTransaction> findByReferenceNumber(String referenceNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowTransaction e WHERE e.id = :id")
    Optional<EscrowTransaction> findByIdWithLock(@Param("id") UUID id);

    Page<EscrowTransaction> findByBuyerIdOrSellerId(UUID buyerId, UUID sellerId, Pageable pageable);

    Page<EscrowTransaction> findByBuyerId(UUID buyerId, Pageable pageable);

    Page<EscrowTransaction> findBySellerId(UUID sellerId, Pageable pageable);

    List<EscrowTransaction> findByStatusAndExpectedCompletionDateBefore(EscrowStatus status, OffsetDateTime cutoffTime);
}
