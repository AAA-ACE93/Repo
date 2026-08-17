package com.escrow.dto;

import com.escrow.domain.enums.EscrowStatus;
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
public class EscrowResponse {
    private UUID id;
    private String referenceNumber;
    private UserResponse buyer;
    private UserResponse seller;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String terms;
    private EscrowStatus status;
    private PaymentStatus paymentStatus;
    private String releaseConditions;
    private OffsetDateTime expectedCompletionDate;
    private OffsetDateTime createdAt;
    private OffsetDateTime fundedAt;
    private OffsetDateTime releasedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime updatedAt;
}
