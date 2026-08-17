package com.escrow.controller;

import com.escrow.dto.PaymentWebhookRequest;
import com.escrow.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment Webhooks", description = "Inbound Payment Provider Webhooks")
public class PaymentController {

    private final EscrowService escrowService;

    @PostMapping("/webhook")
    @Operation(summary = "Process idempotent payment provider webhook")
    public ResponseEntity<Void> processWebhook(@Valid @RequestBody PaymentWebhookRequest request) {
        escrowService.processPaymentWebhook(request);
        return ResponseEntity.ok().build();
    }
}
