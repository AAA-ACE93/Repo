package com.escrow.repository;

import com.escrow.domain.entity.Dispute;
import com.escrow.domain.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
    Optional<Dispute> findByEscrowId(UUID escrowId);
    List<Dispute> findByEscrowIdAndStatusIn(UUID escrowId, List<DisputeStatus> statuses);
    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);
}
