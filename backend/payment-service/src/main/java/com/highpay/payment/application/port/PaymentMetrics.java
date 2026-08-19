package com.highpay.payment.application.port;

public interface PaymentMetrics {

    void recordPaymentCreated();

    void recordIdempotencyHit();

    void recordPaymentProcessingStarted();

    void recordPaymentApproved();

    void recordPaymentRejected();

    void recordPaymentFailed();
}