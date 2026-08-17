package com.escrow.payment;

import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.domain.entity.Payment;
import com.escrow.domain.entity.User;
import com.escrow.domain.enums.EscrowStatus;
import com.escrow.domain.enums.PaymentStatus;
import com.escrow.domain.enums.Role;
import com.escrow.dto.PaymentWebhookRequest;
import com.escrow.repository.PaymentEventRepository;
import com.escrow.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentWebhookIdempotencyTest {

    @Autowired
    private MockPaymentGatewayImpl paymentGateway;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    private Payment payment;

    @BeforeEach
    void setUp() {
        User buyer = User.builder().id(UUID.randomUUID()).name("Buyer").email("b@test.com").passwordHash("hash").role(Role.BUYER).build();
        User seller = User.builder().id(UUID.randomUUID()).name("Seller").email("s@test.com").passwordHash("hash").role(Role.SELLER).build();

        EscrowTransaction escrow = EscrowTransaction.builder()
                .id(UUID.randomUUID())
                .referenceNumber("ESC-2026-IDEM1")
                .buyer(buyer)
                .seller(seller)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(EscrowStatus.AWAITING_PAYMENT)
                .build();

        payment = Payment.builder()
                .paymentReference("PAY-REF-IDEM1")
                .escrow(escrow)
                .buyer(buyer)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .provider("MOCK_GATEWAY")
                .status(PaymentStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("Duplicate webhook requests with same idempotency key are processed idempotently")
    void processWebhook_idempotencyCheck() {
        PaymentWebhookRequest request = PaymentWebhookRequest.builder()
                .eventType("payment.succeeded")
                .paymentReference("PAY-REF-IDEM1")
                .idempotencyKey("WEBHOOK-KEY-10001")
                .payload("Payment Succeeded")
                .build();

        // Save initial payment in repository
        paymentRepository.save(payment);

        // First webhook call
        boolean result1 = paymentGateway.processWebhook(request);
        assertTrue(result1);

        long eventCountFirst = paymentEventRepository.findAll().stream()
                .filter(e -> "WEBHOOK-KEY-10001".equals(e.getIdempotencyKey()))
                .count();
        assertEquals(1, eventCountFirst);

        // Duplicate webhook calls (e.g. sent 5 times by provider)
        for (int i = 0; i < 4; i++) {
            boolean resultDup = paymentGateway.processWebhook(request);
            assertTrue(resultDup);
        }

        // Verify event was only recorded once
        long eventCountFinal = paymentEventRepository.findAll().stream()
                .filter(e -> "WEBHOOK-KEY-10001".equals(e.getIdempotencyKey()))
                .count();
        assertEquals(1, eventCountFinal);
    }
}
