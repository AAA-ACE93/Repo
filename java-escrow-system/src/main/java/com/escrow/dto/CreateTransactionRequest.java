package com.escrow.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public class CreateTransactionRequest {

    @NotNull(message = "Buyer ID is required")
    private Long buyerId;

    @NotNull(message = "Seller ID is required")
    private Long sellerId;

    private Long arbitratorId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "999999999.99", message = "Amount must not exceed 999999999.99")
    private BigDecimal amount;

    @NotNull(message = "Deadline is required")
    @FutureByOneMinute
    private Instant deadline;

    public CreateTransactionRequest() {}

    public CreateTransactionRequest(Long buyerId, Long sellerId, Long arbitratorId, BigDecimal amount, Instant deadline) {
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.arbitratorId = arbitratorId;
        this.amount = amount;
        this.deadline = deadline;
    }

    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }

    public Long getArbitratorId() { return arbitratorId; }
    public void setArbitratorId(Long arbitratorId) { this.arbitratorId = arbitratorId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
}
