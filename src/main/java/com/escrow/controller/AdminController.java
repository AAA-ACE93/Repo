package com.escrow.controller;

import com.escrow.dto.*;
import com.escrow.mapper.LedgerMapper;
import com.escrow.mapper.UserMapper;
import com.escrow.repository.LedgerTransactionRepository;
import com.escrow.repository.UserRepository;
import com.escrow.service.AuditLogService;
import com.escrow.service.DisputeService;
import com.escrow.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Operations", description = "Administration and Monitoring Dashboard Operations")
public class AdminController {

    private final UserRepository userRepository;
    private final EscrowService escrowService;
    private final DisputeService disputeService;
    private final AuditLogService auditLogService;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final UserMapper userMapper;
    private final LedgerMapper ledgerMapper;

    @GetMapping("/users")
    @Operation(summary = "View system users")
    public ResponseEntity<Page<UserResponse>> getUsers(Pageable pageable) {
        return ResponseEntity.ok(userRepository.findAll(pageable).map(userMapper::toResponse));
    }

    @GetMapping("/escrows")
    @Operation(summary = "View all escrow transactions")
    public ResponseEntity<Page<EscrowResponse>> getAllEscrows(
            Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.getUserEscrows(userDetails.getUsername(), pageable));
    }

    @PostMapping("/escrows/{id}/release")
    @Operation(summary = "Admin force release of escrow funds")
    public ResponseEntity<EscrowResponse> adminReleaseEscrow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(escrowService.adminReleaseEscrow(id, userDetails.getUsername()));
    }

    @GetMapping("/disputes")
    @Operation(summary = "Review all disputes")
    public ResponseEntity<Page<DisputeResponse>> getAllDisputes(Pageable pageable) {
        return ResponseEntity.ok(disputeService.getAllDisputes(pageable));
    }

    @PostMapping("/disputes/{id}/resolve")
    @Operation(summary = "Resolve dispute")
    public ResponseEntity<DisputeResponse> resolveDispute(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveDisputeRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(disputeService.resolveDispute(id, request, userDetails.getUsername()));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Review system audit logs")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(Pageable pageable) {
        return ResponseEntity.ok(auditLogService.getAllAuditLogs(pageable));
    }

    @GetMapping("/ledger/transactions")
    @Operation(summary = "Inspect immutable financial ledger transactions")
    public ResponseEntity<Page<LedgerTransactionResponse>> getLedgerTransactions(Pageable pageable) {
        return ResponseEntity.ok(ledgerTransactionRepository.findAll(pageable).map(ledgerMapper::toTransactionResponse));
    }

    @PostMapping("/cron/auto-release")
    @Operation(summary = "Manually trigger auto-release sweep job for eligible escrows")
    public ResponseEntity<Map<String, Object>> triggerAutoRelease() {
        int releasedCount = escrowService.processAutomaticReleases();
        return ResponseEntity.ok(Map.of("message", "Auto-release sweep executed", "releasedCount", releasedCount));
    }
}
