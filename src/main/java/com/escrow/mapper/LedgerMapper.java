package com.escrow.mapper;

import com.escrow.domain.entity.LedgerAccount;
import com.escrow.domain.entity.LedgerEntry;
import com.escrow.domain.entity.LedgerTransaction;
import com.escrow.dto.LedgerAccountResponse;
import com.escrow.dto.LedgerEntryResponse;
import com.escrow.dto.LedgerTransactionResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.stream.Collectors;

@Component
public class LedgerMapper {

    public LedgerAccountResponse toAccountResponse(LedgerAccount account, BigDecimal balance) {
        if (account == null) {
            return null;
        }
        return LedgerAccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .userId(account.getUser() != null ? account.getUser().getId() : null)
                .escrowId(account.getEscrow() != null ? account.getEscrow().getId() : null)
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .balance(balance)
                .createdAt(account.getCreatedAt())
                .build();
    }

    public LedgerTransactionResponse toTransactionResponse(LedgerTransaction transaction) {
        if (transaction == null) {
            return null;
        }
        return LedgerTransactionResponse.builder()
                .id(transaction.getId())
                .transactionReference(transaction.getTransactionReference())
                .escrowId(transaction.getEscrow() != null ? transaction.getEscrow().getId() : null)
                .transactionType(transaction.getTransactionType())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .entries(transaction.getEntries() != null ?
                        transaction.getEntries().stream().map(this::toEntryResponse).collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }

    public LedgerEntryResponse toEntryResponse(LedgerEntry entry) {
        if (entry == null) {
            return null;
        }
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .ledgerAccountId(entry.getLedgerAccount().getId())
                .accountNumber(entry.getLedgerAccount().getAccountNumber())
                .entryType(entry.getEntryType())
                .amount(entry.getAmount())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
