package com.escrow.service;

import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.domain.entity.Payment;
import com.escrow.domain.entity.User;
import com.escrow.domain.enums.EscrowStatus;
import com.escrow.domain.enums.PaymentStatus;
import com.escrow.domain.enums.Role;
import com.escrow.dto.CreateEscrowRequest;
import com.escrow.dto.FundEscrowRequest;
import com.escrow.dto.EscrowResponse;
import com.escrow.exception.InvalidStateException;
import com.escrow.exception.UnauthorizedAccessException;
import com.escrow.mapper.EscrowMapper;
import com.escrow.mapper.PaymentMapper;
import com.escrow.payment.PaymentGateway;
import com.escrow.repository.DisputeRepository;
import com.escrow.repository.EscrowTransactionRepository;
import com.escrow.repository.PaymentRepository;
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
class EscrowServiceTest {

    @Mock
    private EscrowTransactionRepository escrowRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private EscrowMapper escrowMapper = new EscrowMapper(new com.escrow.mapper.UserMapper());

    @Spy
    private PaymentMapper paymentMapper = new PaymentMapper();

    @InjectMocks
    private EscrowServiceImpl escrowService;

    private User buyer;
    private User seller;
    private EscrowTransaction escrow;

    @BeforeEach
    void setUp() {
        buyer = User.builder().id(UUID.randomUUID()).name("Buyer User").email("buyer@test.com").role(Role.BUYER).build();
        seller = User.builder().id(UUID.randomUUID()).name("Seller User").email("seller@test.com").role(Role.SELLER).build();

        escrow = EscrowTransaction.builder()
                .id(UUID.randomUUID())
                .referenceNumber("ESC-2026-TEST1234")
                .buyer(buyer)
                .seller(seller)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .description("Test Web Development Escrow")
                .status(EscrowStatus.AWAITING_PAYMENT)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();
    }

    @Test
    @DisplayName("createEscrow sets initial status AWAITING_PAYMENT and reference number")
    void createEscrow_success() {
        CreateEscrowRequest request = CreateEscrowRequest.builder()
                .sellerId(seller.getId())
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .description("Test Escrow")
                .build();

        when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
        when(userRepository.findById(seller.getId())).thenReturn(Optional.of(seller));
        when(escrowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EscrowResponse response = escrowService.createEscrow(request, "buyer@test.com");

        assertNotNull(response);
        assertEquals(EscrowStatus.AWAITING_PAYMENT, response.getStatus());
        assertTrue(response.getReferenceNumber().startsWith("ESC-2026-"));
    }

    @Test
    @DisplayName("releaseEscrow transitions status to RELEASED and records ledger release entry")
    void releaseEscrow_success() {
        escrow.setStatus(EscrowStatus.AWAITING_RELEASE);

        when(escrowRepository.findByIdWithLock(escrow.getId())).thenReturn(Optional.of(escrow));
        when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
        when(escrowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EscrowResponse response = escrowService.releaseEscrow(escrow.getId(), "buyer@test.com");

        assertNotNull(response);
        assertEquals(EscrowStatus.RELEASED, response.getStatus());
        verify(ledgerService, times(1)).recordEscrowRelease(any(), eq(new BigDecimal("500.00")));
    }

    @Test
    @DisplayName("releaseEscrow fails if escrow is already RELEASED")
    void releaseEscrow_alreadyReleased_throwsException() {
        escrow.setStatus(EscrowStatus.RELEASED);

        when(escrowRepository.findByIdWithLock(escrow.getId())).thenReturn(Optional.of(escrow));
        when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));

        assertThrows(InvalidStateException.class, () -> escrowService.releaseEscrow(escrow.getId(), "buyer@test.com"));
    }

    @Test
    @DisplayName("releaseEscrow fails if escrow is DISPUTED")
    void releaseEscrow_whenDisputed_throwsException() {
        escrow.setStatus(EscrowStatus.DISPUTED);

        when(escrowRepository.findByIdWithLock(escrow.getId())).thenReturn(Optional.of(escrow));
        when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));

        assertThrows(InvalidStateException.class, () -> escrowService.releaseEscrow(escrow.getId(), "buyer@test.com"));
    }

    @Test
    @DisplayName("unauthorized user cannot release escrow")
    void releaseEscrow_unauthorizedUser_throwsException() {
        User thirdParty = User.builder().id(UUID.randomUUID()).email("other@test.com").role(Role.BUYER).build();
        escrow.setStatus(EscrowStatus.AWAITING_RELEASE);

        when(escrowRepository.findByIdWithLock(escrow.getId())).thenReturn(Optional.of(escrow));
        when(userRepository.findByEmail("other@test.com")).thenReturn(Optional.of(thirdParty));

        assertThrows(UnauthorizedAccessException.class, () -> escrowService.releaseEscrow(escrow.getId(), "other@test.com"));
    }
}
