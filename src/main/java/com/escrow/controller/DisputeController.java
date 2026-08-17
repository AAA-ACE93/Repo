package com.escrow.controller;

import com.escrow.dto.DisputeEvidenceResponse;
import com.escrow.dto.DisputeResponse;
import com.escrow.dto.ResolveDisputeRequest;
import com.escrow.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
@Tag(name = "Disputes", description = "Dispute resolution and evidence submission")
public class DisputeController {

    private final DisputeService disputeService;

    @GetMapping("/{id}")
    @Operation(summary = "Get dispute details by dispute ID")
    public ResponseEntity<DisputeResponse> getDisputeById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(disputeService.getDisputeById(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/evidence")
    @Operation(summary = "Upload evidence file for a dispute")
    public ResponseEntity<DisputeEvidenceResponse> attachEvidence(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(disputeService.attachEvidence(id, file, userDetails.getUsername()));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin resolve dispute")
    public ResponseEntity<DisputeResponse> resolveDispute(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveDisputeRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(disputeService.resolveDispute(id, request, userDetails.getUsername()));
    }
}
