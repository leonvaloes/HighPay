package com.highpay.provider.interfaces.rest;

public record ProviderPaymentResponse(
        String status,
        String providerTransactionId,
        String message
) {
}