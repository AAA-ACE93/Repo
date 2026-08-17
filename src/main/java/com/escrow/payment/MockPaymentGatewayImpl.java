package com.escrow.payment;

import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.domain.entity.Payment;
import com.escrow.domain.entity.PaymentEvent;
import com.escrow.domain.entity.User;
import com.escrow.domain.enums.PaymentStatus;
import com.escrow.dto.PaymentWebhookRequest;
import com.escrow.exception.ResourceNotFoundException;
import com.escrow.repository.PaymentEventRepository;
import com.escrow.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockPaymentGatewayImpl implements PaymentGateway {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;

    @Override
    @Transactional
    public Payment createPayment(EscrowTransaction escrow, User buyer, BigDecimal amount, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent payment lookup returned existing payment for key {}", idempotencyKey);
                return existing.get();
            }
        }

        String paymentRef = "PAY-MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Payment payment = Payment.builder()
                .paymentReference(paymentRef)
                .escrow(escrow)
                .buyer(buyer)
                .amount(amount)
                .currency(escrow.getCurrency())
                .provider("MOCK_PAYMENT_GATEWAY")
                .status(PaymentStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();

        Payment saved = paymentRepository.save(payment);

        PaymentEvent event = PaymentEvent.builder()
                .payment(saved)
                .eventType("PAYMENT_CREATED")
                .idempotencyKey(idempotencyKey)
                .payload("Payment created with reference: " + paymentRef)
                .build();
        paymentEventRepository.save(event);

        return saved;
    }

    @Override
    @Transactional
    public Payment verifyPayment(String paymentReference) {
        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for reference: " + paymentReference));

        payment.setStatus(PaymentStatus.CAPTURED);
        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public Payment refundPayment(String paymentReference, BigDecimal amount, String idempotencyKey) {
        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for reference: " + paymentReference));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (paymentEventRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
                log.info("Idempotent refund webhook already executed for key {}", idempotencyKey);
                return payment;
            }
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        Payment saved = paymentRepository.save(payment);

        PaymentEvent event = PaymentEvent.builder()
                .payment(saved)
                .eventType("PAYMENT_REFUNDED")
                .idempotencyKey(idempotencyKey)
                .payload("Payment refunded for amount: " + amount)
                .build();
        paymentEventRepository.save(event);

        return saved;
    }

    @Override
    @Transactional
    public Payment capturePayment(String paymentReference) {
        return verifyPayment(paymentReference);
    }

    @Override
    @Transactional
    public boolean processWebhook(PaymentWebhookRequest webhookRequest) {
        if (paymentEventRepository.findByIdempotencyKey(webhookRequest.getIdempotencyKey()).isPresent()) {
            log.info("Webhook with idempotencyKey {} already processed. Skipping.", webhookRequest.getIdempotencyKey());
            return true;
        }

        Payment payment = paymentRepository.findByPaymentReference(webhookRequest.getPaymentReference())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + webhookRequest.getPaymentReference()));

        if ("payment.succeeded".equalsIgnoreCase(webhookRequest.getEventType())) {
            payment.setStatus(PaymentStatus.CAPTURED);
        } else if ("payment.failed".equalsIgnoreCase(webhookRequest.getEventType())) {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);

        PaymentEvent event = PaymentEvent.builder()
                .payment(payment)
                .eventType(webhookRequest.getEventType())
                .idempotencyKey(webhookRequest.getIdempotencyKey())
                .payload(webhookRequest.getPayload())
                .build();
        paymentEventRepository.save(event);

        return true;
    }
}
