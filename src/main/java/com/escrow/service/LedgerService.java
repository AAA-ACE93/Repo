package com.escrow.service;

import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.domain.entity.LedgerAccount;
import com.escrow.domain.entity.LedgerTransaction;
import com.escrow.domain.entity.User;
import com.escrow.domain.enums.LedgerAccountType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerService {
    LedgerAccount getOrCreateAccount(User user, EscrowTransaction escrow, LedgerAccountType accountType, String currency);

    LedgerTransaction recordBuyerPayment(EscrowTransaction escrow, BigDecimal amount);

    LedgerTransaction recordEscrowRelease(EscrowTransaction escrow, BigDecimal amount);

    LedgerTransaction recordEscrowRefund(EscrowTransaction escrow, BigDecimal amount);

    LedgerTransaction recordDisputeSplit(EscrowTransaction escrow, BigDecimal buyerAmount, BigDecimal sellerAmount);

    BigDecimal getAccountBalance(UUID accountId);

    List<LedgerTransaction> getEscrowLedgerTransactions(UUID escrowId);
}
