package com.highpay.processor.application.port;

public interface PaymentProcessorMetrics {

    void recordPaymentCreatedEventConsumed();

    void recordDuplicateEventSkipped();

    void recordProviderApproved();

    void recordProviderRejected();

    void recordProviderFailed();

    void recordProcessingFailed();

    void recordPaymentFailNotificationFailed();

}