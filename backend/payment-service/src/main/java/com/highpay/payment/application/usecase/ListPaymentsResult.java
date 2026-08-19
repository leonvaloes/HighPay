package com.highpay.payment.application.usecase;

import java.util.List;

import com.highpay.payment.domain.model.Payment;

public record ListPaymentsResult(
        List<Payment> payments,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}