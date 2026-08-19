package com.escrow.repository;

import com.escrow.model.Dispute;
import com.escrow.model.DisputeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    Optional<Dispute> findByTransactionId(Long transactionId);

    boolean existsByTransactionIdAndStatusIn(Long transactionId, List<DisputeStatus> statuses);
}
