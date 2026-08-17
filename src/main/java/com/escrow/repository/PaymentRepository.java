package com.escrow.repository;

import com.escrow.domain.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByPaymentReference(String paymentReference);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findByEscrowId(UUID escrowId);
}
