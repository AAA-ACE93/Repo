package com.escrow.controller;

import com.escrow.dto.ResolveDisputeRequest;
import com.escrow.model.Dispute;
import com.escrow.service.DisputeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/disputes")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping
    public ResponseEntity<List<Dispute>> getAllDisputes() {
        return ResponseEntity.ok(disputeService.getAllDisputes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dispute> getDisputeById(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.getDisputeById(id));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Dispute> resolveDispute(@PathVariable Long id,
                                                   @Valid @RequestBody ResolveDisputeRequest request) {
        Dispute dispute = disputeService.resolveDispute(id, request.getResolution());
        return ResponseEntity.ok(dispute);
    }
}
