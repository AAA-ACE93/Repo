package com.escrow.controller;

import com.escrow.dto.*;
import com.escrow.model.Dispute;
import com.escrow.model.Transaction;
import com.escrow.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        Transaction tx = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getTransactionsByUserId(@RequestParam Long userId) {
        return ResponseEntity.ok(transactionService.getTransactionsByUserId(userId));
    }

    @PostMapping("/{id}/fund")
    public ResponseEntity<Transaction> fundTransaction(@PathVariable Long id,
                                                        @Valid @RequestBody FundTransactionRequest request) {
        Transaction tx = transactionService.fundTransaction(id, request.getRequestingUserId());
        return ResponseEntity.ok(tx);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Transaction> confirmTransaction(@PathVariable Long id,
                                                           @Valid @RequestBody ConfirmTransactionRequest request) {
        Transaction tx = transactionService.confirmTransaction(id, request.getRequestingUserId());
        return ResponseEntity.ok(tx);
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<Dispute> fileDispute(@PathVariable Long id,
                                                @Valid @RequestBody FileDisputeRequest request) {
        Dispute dispute = transactionService.fileDispute(id, request.getRaisedByUserId(), request.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(dispute);
    }
}
