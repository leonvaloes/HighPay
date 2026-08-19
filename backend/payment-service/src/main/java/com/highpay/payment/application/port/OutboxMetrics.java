package com.highpay.payment.application.port;
public interface OutboxMetrics {
    void recordOutboxEventPublished();
    void recordOutboxEventFailed();
}