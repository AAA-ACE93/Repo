package com.escrow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookRequest {

    @NotBlank(message = "Event type is required")
    private String eventType;

    @NotBlank(message = "Payment reference is required")
    private String paymentReference;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    private String signature;

    private String payload;
}
