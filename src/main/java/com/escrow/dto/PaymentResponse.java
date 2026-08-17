package com.escrow.dto;

import com.escrow.domain.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private String paymentReference;
    private UUID escrowId;
    private UUID buyerId;
    private BigDecimal amount;
    private String currency;
    private String provider;
    private PaymentStatus status;
    private String idempotencyKey;
    private OffsetDateTime createdAt;
}
