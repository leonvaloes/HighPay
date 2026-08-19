package com.highpay.payment.interfaces.rest.request;

import java.math.BigDecimal;

import com.highpay.payment.domain.enums.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreatePaymentRequest(

        @NotBlank(message = "Merchant is required")
        String merchantId,

        @NotNull(message = "Amount is required")
        @DecimalMin(
                value = "0.01",
                message = "Amount must be greater than zero"
        )
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "Currency must contain 3 uppercase letters"
        )
        String currency,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod

) {
}