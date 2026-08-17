package com.escrow.dto;

import com.escrow.domain.enums.LedgerTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionResponse {
    private UUID id;
    private String transactionReference;
    private UUID escrowId;
    private LedgerTransactionType transactionType;
    private String description;
    private OffsetDateTime createdAt;
    private List<LedgerEntryResponse> entries;
}
