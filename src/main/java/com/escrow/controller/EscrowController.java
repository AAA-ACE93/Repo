package com.escrow.controller;

import com.escrow.dto.*;
import com.escrow.service.DisputeService;
import com.escrow.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/escrows")
@RequiredArgsConstructor
@Tag(name = "Escrow Transactions", description = "Core Escrow Transaction Lifecycle and Management")
public class EscrowController {

    private final EscrowService escrowService;
    private final DisputeService disputeService;

    @PostMapping
    @Operation(summary = "Create a new escrow transaction")
    public ResponseEntity<EscrowResponse> createEscrow(
            @Valid @RequestBody CreateEscrowRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(escrowService.createEscrow(request, userDetails.getUsername()));
    }

    @GetMapping
    @Operation(summary = "Get current user's escrow transactions")
    public ResponseEntity<Page<EscrowResponse>> getUserEscrows(
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.getUserEscrows(userDetails.getUsername(), pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get escrow transaction by ID")
    public ResponseEntity<EscrowResponse> getEscrowById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.getEscrowById(id, userDetails.getUsername()));
    }

    @GetMapping("/ref/{referenceNumber}")
    @Operation(summary = "Get escrow transaction by public reference number")
    public ResponseEntity<EscrowResponse> getEscrowByReference(
            @PathVariable String referenceNumber,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.getEscrowByReference(referenceNumber, userDetails.getUsername()));
    }

    @PostMapping("/{id}/fund")
    @Operation(summary = "Fund an escrow transaction")
    public ResponseEntity<PaymentResponse> fundEscrow(
            @PathVariable UUID id,
            @Valid @RequestBody FundEscrowRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.fundEscrow(id, request, userDetails.getUsername()));
    }

    @PostMapping("/{id}/start-in-progress")
    @Operation(summary = "Mark escrow as in-progress")
    public ResponseEntity<EscrowResponse> startInProgress(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.startInProgress(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/request-release")
    @Operation(summary = "Seller requests release of escrow funds")
    public ResponseEntity<EscrowResponse> requestRelease(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.requestRelease(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Buyer approves release of escrow funds")
    public ResponseEntity<EscrowResponse> releaseEscrow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.releaseEscrow(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund escrow funds")
    public ResponseEntity<EscrowResponse> refundEscrow(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "Buyer/Seller requested refund") String reason,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.refundEscrow(id, userDetails.getUsername(), reason));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel unfunded escrow")
    public ResponseEntity<EscrowResponse> cancelEscrow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.cancelEscrow(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/disputes")
    @Operation(summary = "Open a dispute on an escrow transaction")
    public ResponseEntity<DisputeResponse> openDispute(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDisputeRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(disputeService.openDispute(id, request, userDetails.getUsername()));
    }

    @GetMapping("/{id}/disputes")
    @Operation(summary = "Get dispute details for an escrow transaction")
    public ResponseEntity<DisputeResponse> getDisputeByEscrow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(disputeService.getDisputeByEscrow(id, userDetails.getUsername()));
    }
}
