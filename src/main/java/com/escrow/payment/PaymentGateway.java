package com.escrow.payment;

import com.escrow.domain.entity.EscrowTransaction;
import com.escrow.domain.entity.Payment;
import com.escrow.domain.entity.User;
import com.escrow.dto.PaymentWebhookRequest;

import java.math.BigDecimal;

public interface PaymentGateway {
    Payment createPayment(EscrowTransaction escrow, User buyer, BigDecimal amount, String idempotencyKey);
    Payment verifyPayment(String paymentReference);
    Payment refundPayment(String paymentReference, BigDecimal amount, String idempotencyKey);
    Payment capturePayment(String paymentReference);
    boolean processWebhook(PaymentWebhookRequest webhookRequest);
}
