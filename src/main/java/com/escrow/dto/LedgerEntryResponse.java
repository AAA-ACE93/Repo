package com.escrow.dto;

import com.escrow.domain.enums.EntryType;
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
public class LedgerEntryResponse {
    private UUID id;
    private UUID ledgerAccountId;
    private String accountNumber;
    private EntryType entryType;
    private BigDecimal amount;
    private OffsetDateTime createdAt;
}
