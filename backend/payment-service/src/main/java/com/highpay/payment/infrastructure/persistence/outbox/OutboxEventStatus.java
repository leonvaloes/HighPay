package com.highpay.payment.infrastructure.persistence.outbox;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}