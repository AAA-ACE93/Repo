package com.escrow.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "disputes")
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(optional = false)
    @JoinColumn(name = "raised_by_id", nullable = false)
    private User raisedBy;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status;

    @Enumerated(EnumType.STRING)
    private DisputeResolution resolution;

    @Column(nullable = false, updatable = false)
    private Instant filedAt;

    private Instant resolvedAt;

    @PrePersist
    protected void prePersist() {
        this.filedAt = Instant.now();
    }

    public Dispute() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public User getRaisedBy() { return raisedBy; }
    public void setRaisedBy(User raisedBy) { this.raisedBy = raisedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public DisputeStatus getStatus() { return status; }
    public void setStatus(DisputeStatus status) { this.status = status; }

    public DisputeResolution getResolution() { return resolution; }
    public void setResolution(DisputeResolution resolution) { this.resolution = resolution; }

    public Instant getFiledAt() { return filedAt; }
    public void setFiledAt(Instant filedAt) { this.filedAt = filedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
