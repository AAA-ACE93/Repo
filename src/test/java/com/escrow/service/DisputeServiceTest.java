package com.escrow.service;

import com.escrow.domain.entity.Dispute;
import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.domain.entity.User;
import com.escrow.domain.enums.DisputeResolution;
import com.escrow.domain.enums.DisputeStatus;
import com.escrow.domain.enums.EscrowStatus;
import com.escrow.domain.enums.Role;
import com.escrow.dto.CreateDisputeRequest;
import com.escrow.dto.DisputeResponse;
import com.escrow.dto.ResolveDisputeRequest;
import com.escrow.exception.InvalidStateException;
import com.escrow.integration.storage.StorageService;
import com.escrow.mapper.DisputeMapper;
import com.escrow.mapper.UserMapper;
import com.escrow.repository.DisputeEvidenceRepository;
import com.escrow.repository.DisputeRepository;
import com.escrow.repository.EscrowTransactionRepository;
import com.escrow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeEvidenceRepository evidenceRepository;

    @Mock
    private EscrowTransactionRepository escrowRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private StorageService storageService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private DisputeMapper disputeMapper = new DisputeMapper(new UserMapper());

    @InjectMocks
    private DisputeServiceImpl disputeService;

    private User buyer;
    private User seller;
    private User admin;
    private EscrowTransaction escrow;
    private Dispute dispute;

    @BeforeEach
    void setUp() {
        buyer = User.builder().id(UUID.randomUUID()).name("Buyer").email("buyer@test.com").role(Role.BUYER).build();
        seller = User.builder().id(UUID.randomUUID()).name("Seller").email("seller@test.com").role(Role.SELLER).build();
        admin = User.builder().id(UUID.randomUUID()).name("Admin").email("admin@test.com").role(Role.ADMIN).build();

        escrow = EscrowTransaction.builder()
                .id(UUID.randomUUID())
                .referenceNumber("ESC-2026-DISP1")
                .buyer(buyer)
                .seller(seller)
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .status(EscrowStatus.FUNDED)
                .build();

        dispute = Dispute.builder()
                .id(UUID.randomUUID())
                .escrow(escrow)
                .openedBy(buyer)
                .reason("Services not delivered")
                .status(DisputeStatus.OPEN)
                .build();
    }

    @Test
    @DisplayName("openDispute transitions escrow status to DISPUTED")
    void openDispute_success() {
        CreateDisputeRequest request = CreateDisputeRequest.builder()
                .reason("Services not delivered")
                .description("No update from seller")
                .build();

        when(escrowRepository.findByIdWithLock(escrow.getId())).thenReturn(Optional.of(escrow));
        when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
        when(disputeRepository.findByEscrowId(escrow.getId())).thenReturn(Optional.empty());
        when(disputeRepository.save(any())).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });

        DisputeResponse response = disputeService.openDispute(escrow.getId(), request, "buyer@test.com");

        assertNotNull(response);
        assertEquals(DisputeStatus.OPEN, response.getStatus());
        assertEquals(EscrowStatus.DISPUTED, escrow.getStatus());
    }

    @Test
    @DisplayName("resolveDispute REFUND_BUYER refunds buyer and updates escrow to REFUNDED")
    void resolveDispute_refundBuyer() {
        ResolveDisputeRequest request = ResolveDisputeRequest.builder()
                .resolution(DisputeResolution.REFUND_BUYER)
                .adminDecision("Seller failed to provide evidence")
                .build();

        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(escrowRepository.findByIdWithLock(escrow.getId())).thenReturn(Optional.of(escrow));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DisputeResponse response = disputeService.resolveDispute(dispute.getId(), request, "admin@test.com");

        assertNotNull(response);
        assertEquals(DisputeStatus.RESOLVED_BUYER, response.getStatus());
        assertEquals(EscrowStatus.REFUNDED, escrow.getStatus());
        verify(ledgerService, times(1)).recordEscrowRefund(escrow, escrow.getAmount());
    }
}
