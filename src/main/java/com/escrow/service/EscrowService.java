package com.escrow.service;

import com.escrow.dto.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface EscrowService {
    EscrowResponse createEscrow(CreateEscrowRequest request, String buyerEmail);

    EscrowResponse getEscrowById(UUID id, String userEmail);

    EscrowResponse getEscrowByReference(String referenceNumber, String userEmail);

    Page<EscrowResponse> getUserEscrows(String userEmail, Pageable pageable);

    PaymentResponse fundEscrow(UUID id, FundEscrowRequest request, String buyerEmail);

    void processPaymentWebhook(PaymentWebhookRequest request);

    EscrowResponse startInProgress(UUID id, String userEmail);

    EscrowResponse requestRelease(UUID id, String sellerEmail);

    EscrowResponse releaseEscrow(UUID id, String buyerEmail);

    EscrowResponse adminReleaseEscrow(UUID id, String adminEmail);

    EscrowResponse refundEscrow(UUID id, String userEmail, String reason);

    EscrowResponse cancelEscrow(UUID id, String userEmail);

    int processAutomaticReleases();
}
