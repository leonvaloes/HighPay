package com.highpay.processor.application.model;

public record ProviderPaymentResult(
        String status,
        String providerTransactionId
) {

    public boolean approved() {
        return "SUCCESS".equalsIgnoreCase(status);
    }

    public boolean rejected() {
        return "REJECTED".equalsIgnoreCase(status);
    }
}