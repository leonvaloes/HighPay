package com.highpay.payment.application.usecase;

import com.highpay.payment.domain.model.Payment;

public record CreatePaymentResult(
        Payment payment,
        boolean created
) {
}
