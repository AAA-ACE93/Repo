package com.escrow.dto;

import com.escrow.domain.enums.LedgerAccountType;
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
public class LedgerAccountResponse {
    private UUID id;
    private String accountNumber;
    private UUID userId;
    private UUID escrowId;
    private LedgerAccountType accountType;
    private String currency;
    private BigDecimal balance;
    private OffsetDateTime createdAt;
}
