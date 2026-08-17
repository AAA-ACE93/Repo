package com.escrow.domain.enums;

public enum EscrowStatus {
    CREATED,
    AWAITING_PAYMENT,
    FUNDED,
    IN_PROGRESS,
    AWAITING_RELEASE,
    RELEASED,
    REFUNDED,
    DISPUTED,
    CANCELLED,
    EXPIRED
}
