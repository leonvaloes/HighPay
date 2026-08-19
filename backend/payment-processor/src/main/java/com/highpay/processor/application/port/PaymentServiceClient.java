package com.highpay.processor.application.port;

import java.util.UUID;

public interface PaymentServiceClient {

    void markAsProcessing(UUID paymentId);

    void approve(UUID paymentId, String providerTransactionId);

    void reject(UUID paymentId, String providerTransactionId);

    void fail(UUID paymentId);
}