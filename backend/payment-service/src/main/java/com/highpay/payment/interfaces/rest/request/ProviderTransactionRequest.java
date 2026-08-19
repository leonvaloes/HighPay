package com.highpay.payment.interfaces.rest.request;

import jakarta.validation.constraints.NotBlank;

public record ProviderTransactionRequest(
        @NotBlank(message = "Provider transaction id is required")
        String providerTransactionId
) {
}