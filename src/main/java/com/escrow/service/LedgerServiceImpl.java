package com.escrow.service;

import com.escrow.domain.entity.*;
import com.escrow.domain.enums.EntryType;
import com.escrow.domain.enums.LedgerAccountType;
import com.escrow.domain.enums.LedgerTransactionType;
import com.escrow.exception.ConflictException;
import com.escrow.repository.LedgerAccountRepository;
import com.escrow.repository.LedgerEntryRepository;
import com.escrow.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerServiceImpl implements LedgerService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    @Override
    @Transactional
    public LedgerAccount getOrCreateAccount(User user, EscrowTransaction escrow, LedgerAccountType accountType, String currency) {
        if (escrow != null) {
            var existing = accountRepository.findByEscrowIdAndAccountType(escrow.getId(), accountType);
            if (existing.isPresent()) {
                return existing.get();
            }
        } else if (user != null) {
            var existing = accountRepository.findByUserIdAndAccountType(user.getId(), accountType);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        String accNo = "ACC-" + accountType.name() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LedgerAccount account = LedgerAccount.builder()
                .accountNumber(accNo)
                .user(user)
                .escrow(escrow)
                .accountType(accountType)
                .currency(currency != null ? currency : "USD")
                .build();

        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public LedgerTransaction recordBuyerPayment(EscrowTransaction escrow, BigDecimal amount) {
        validateAmount(amount);

        LedgerAccount buyerAccount = getOrCreateAccount(escrow.getBuyer(), null, LedgerAccountType.BUYER_PAYMENT, escrow.getCurrency());
        LedgerAccount escrowAccount = getOrCreateAccount(null, escrow, LedgerAccountType.ESCROW_HOLDING, escrow.getCurrency());

        LedgerTransaction tx = LedgerTransaction.builder()
                .transactionReference("TX-PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .escrow(escrow)
                .transactionType(LedgerTransactionType.FUNDING)
                .description("Buyer payment for escrow " + escrow.getReferenceNumber())
                .entries(new ArrayList<>())
                .build();

        // Debit Buyer Account, Credit Escrow Holding Account
        LedgerEntry debitEntry = LedgerEntry.builder()
                .ledgerTransaction(tx)
                .ledgerAccount(buyerAccount)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .ledgerTransaction(tx)
                .ledgerAccount(escrowAccount)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .build();

        tx.getEntries().add(debitEntry);
        tx.getEntries().add(creditEntry);

        validateLedgerBalance(tx);
        return transactionRepository.save(tx);
    }

    @Override
    @Transactional
    public LedgerTransaction recordEscrowRelease(EscrowTransaction escrow, BigDecimal amount) {
        validateAmount(amount);

        LedgerAccount escrowAccount = getOrCreateAccount(null, escrow, LedgerAccountType.ESCROW_HOLDING, escrow.getCurrency());
        LedgerAccount sellerAccount = getOrCreateAccount(escrow.getSeller(), null, LedgerAccountType.SELLER_PAYOUT, escrow.getCurrency());

        LedgerTransaction tx = LedgerTransaction.builder()
                .transactionReference("TX-REL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .escrow(escrow)
                .transactionType(LedgerTransactionType.RELEASE)
                .description("Release funds to seller for escrow " + escrow.getReferenceNumber())
                .entries(new ArrayList<>())
                .build();

        // Debit Escrow Holding Account, Credit Seller Payout Account
        LedgerEntry debitEntry = LedgerEntry.builder()
                .ledgerTransaction(tx)
                .ledgerAccount(escrowAccount)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .ledgerTransaction(tx)
                .ledgerAccount(sellerAccount)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .build();

        tx.getEntries().add(debitEntry);
        tx.getEntries().add(creditEntry);

        validateLedgerBalance(tx);
        return transactionRepository.save(tx);
    }

    @Override
    @Transactional
    public LedgerTransaction recordEscrowRefund(EscrowTransaction escrow, BigDecimal amount) {
        validateAmount(amount);

        LedgerAccount escrowAccount = getOrCreateAccount(null, escrow, LedgerAccountType.ESCROW_HOLDING, escrow.getCurrency());
        LedgerAccount buyerAccount = getOrCreateAccount(escrow.getBuyer(), null, LedgerAccountType.BUYER_PAYMENT, escrow.getCurrency());

        LedgerTransaction tx = LedgerTransaction.builder()
                .transactionReference("TX-REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .escrow(escrow)
                .transactionType(LedgerTransactionType.REFUND)
                .description("Refund funds to buyer for escrow " + escrow.getReferenceNumber())
                .entries(new ArrayList<>())
                .build();

        // Debit Escrow Holding Account, Credit Buyer Account
        LedgerEntry debitEntry = LedgerEntry.builder()
                .ledgerTransaction(tx)
                .ledgerAccount(escrowAccount)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .ledgerTransaction(tx)
                .ledgerAccount(buyerAccount)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .build();

        tx.getEntries().add(debitEntry);
        tx.getEntries().add(creditEntry);

        validateLedgerBalance(tx);
        return transactionRepository.save(tx);
    }

    @Override
    @Transactional
    public LedgerTransaction recordDisputeSplit(EscrowTransaction escrow, BigDecimal buyerAmount, BigDecimal sellerAmount) {
        validateAmount(buyerAmount);
        validateAmount(sellerAmount);

        BigDecimal totalAmount = buyerAmount.add(sellerAmount);

        LedgerAccount escrowAccount = getOrCreateAccount(null, escrow, LedgerAccountType.ESCROW_HOLDING, escrow.getCurrency());
        LedgerAccount buyerAccount = getOrCreateAccount(escrow.getBuyer(), null, LedgerAccountType.BUYER_PAYMENT, escrow.getCurrency());
        LedgerAccount sellerAccount = getOrCreateAccount(escrow.getSeller(), null, LedgerAccountType.SELLER_PAYOUT, escrow.getCurrency());

        LedgerTransaction tx = LedgerTransaction.builder()
                .transactionReference("TX-DSP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .escrow(escrow)
                .transactionType(LedgerTransactionType.DISPUTE_ADJUSTMENT)
                .description("Dispute split resolution for escrow " + escrow.getReferenceNumber())
                .entries(new ArrayList<>())
                .build();

        // Debit total from Escrow Holding
        LedgerEntry debitEscrow = LedgerEntry.builder()
                .ledgerTransaction(tx)
                .ledgerAccount(escrowAccount)
                .entryType(EntryType.DEBIT)
                .amount(totalAmount)
                .build();

        // Credit buyer portion
        LedgerEntry creditBuyer = LedgerEntry.builder()
                .ledgerTransaction(tx)
                .ledgerAccount(buyerAccount)
                .entryType(EntryType.CREDIT)
                .amount(buyerAmount)
                .build();

        // Credit seller portion
        LedgerEntry creditSeller = LedgerEntry.builder()
                .ledgerTransaction(tx)
                .ledgerAccount(sellerAccount)
                .entryType(EntryType.CREDIT)
                .amount(sellerAmount)
                .build();

        tx.getEntries().add(debitEscrow);
        tx.getEntries().add(creditBuyer);
        tx.getEntries().add(creditSeller);

        validateLedgerBalance(tx);
        return transactionRepository.save(tx);
    }

    @Override
    public BigDecimal getAccountBalance(UUID accountId) {
        return entryRepository.calculateBalanceForAccount(accountId);
    }

    @Override
    public List<LedgerTransaction> getEscrowLedgerTransactions(UUID escrowId) {
        return transactionRepository.findByEscrowId(escrowId);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private void validateLedgerBalance(LedgerTransaction tx) {
        BigDecimal totalDebit = tx.getEntries().stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = tx.getEntries().stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ConflictException("Ledger transaction is unbalanced! Debits: " + totalDebit + " != Credits: " + totalCredit);
        }
    }
}
