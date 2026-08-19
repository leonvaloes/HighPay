package com.highpay.provider.interfaces.rest;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderPaymentRequest(
        @NotNull(message = "Payment id is required")
        UUID paymentId,

        @NotNull(message = "Amount is required")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        String currency
) {
}