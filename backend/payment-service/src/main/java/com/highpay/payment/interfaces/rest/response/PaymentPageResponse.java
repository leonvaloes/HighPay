package com.highpay.payment.interfaces.rest.response;

import java.util.List;

import com.highpay.payment.application.usecase.ListPaymentsResult;

public record PaymentPageResponse(
        List<PaymentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static PaymentPageResponse from(ListPaymentsResult result) {
        List<PaymentResponse> items = result.payments()
                .stream()
                .map(PaymentResponse::from)
                .toList();

        return new PaymentPageResponse(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}