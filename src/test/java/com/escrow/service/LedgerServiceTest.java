package com.escrow.service;

import com.escrow.domain.entity.*;
import com.escrow.domain.enums.EscrowStatus;
import com.escrow.domain.enums.Role;

import com.escrow.repository.LedgerAccountRepository;
import com.escrow.repository.LedgerEntryRepository;
import com.escrow.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerAccountRepository accountRepository;

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository entryRepository;

    @InjectMocks
    private LedgerServiceImpl ledgerService;

    private User buyer;
    private User seller;
    private EscrowTransaction escrow;

    @BeforeEach
    void setUp() {
        buyer = User.builder().id(UUID.randomUUID()).name("Buyer").email("buyer@example.com").role(Role.BUYER).build();
        seller = User.builder().id(UUID.randomUUID()).name("Seller").email("seller@example.com").role(Role.SELLER).build();
        escrow = EscrowTransaction.builder()
                .id(UUID.randomUUID())
                .referenceNumber("ESC-2026-TEST1111")
                .buyer(buyer)
                .seller(seller)
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .status(EscrowStatus.AWAITING_PAYMENT)
                .build();
    }

    @Test
    @DisplayName("recordBuyerPayment creates balanced DEBIT and CREDIT ledger entries")
    void recordBuyerPayment_createsBalancedEntries() {
        when(accountRepository.findByUserIdAndAccountType(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByEscrowIdAndAccountType(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LedgerTransaction tx = ledgerService.recordBuyerPayment(escrow, new BigDecimal("1000.00"));

        assertNotNull(tx);
        assertEquals(2, tx.getEntries().size());

        BigDecimal debitSum = tx.getEntries().stream()
                .filter(e -> e.getEntryType() == com.escrow.domain.enums.EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditSum = tx.getEntries().stream()
                .filter(e -> e.getEntryType() == com.escrow.domain.enums.EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, debitSum.compareTo(creditSum));
        assertEquals(0, debitSum.compareTo(new BigDecimal("1000.00")));
    }

    @Test
    @DisplayName("recordDisputeSplit creates balanced entries for buyer and seller portions")
    void recordDisputeSplit_createsBalancedEntries() {
        when(accountRepository.findByUserIdAndAccountType(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.findByEscrowIdAndAccountType(any(), any())).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LedgerTransaction tx = ledgerService.recordDisputeSplit(escrow, new BigDecimal("600.00"), new BigDecimal("400.00"));

        assertNotNull(tx);
        assertEquals(3, tx.getEntries().size());

        BigDecimal debitSum = tx.getEntries().stream()
                .filter(e -> e.getEntryType() == com.escrow.domain.enums.EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal creditSum = tx.getEntries().stream()
                .filter(e -> e.getEntryType() == com.escrow.domain.enums.EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, debitSum.compareTo(creditSum));
        assertEquals(0, debitSum.compareTo(new BigDecimal("1000.00")));
    }
}
