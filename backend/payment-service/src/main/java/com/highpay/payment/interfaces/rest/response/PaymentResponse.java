package com.highpay.payment.interfaces.rest.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.domain.enums.PaymentStatus;
import com.highpay.payment.domain.model.Payment;

public record PaymentResponse(

        UUID id,
        String merchantId,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String providerTransactionId,
        Instant createdAt,
        Instant updatedAt

) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getProviderTransactionId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}