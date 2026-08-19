package com.escrow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "escrow_accounts")
public class EscrowAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal lockedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscrowStatus status;

    public EscrowAccount() {}

    public EscrowAccount(Transaction transaction, BigDecimal lockedAmount, EscrowStatus status) {
        this.transaction = transaction;
        this.lockedAmount = lockedAmount;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public BigDecimal getLockedAmount() { return lockedAmount; }
    public void setLockedAmount(BigDecimal lockedAmount) { this.lockedAmount = lockedAmount; }

    public EscrowStatus getStatus() { return status; }
    public void setStatus(EscrowStatus status) { this.status = status; }
}
