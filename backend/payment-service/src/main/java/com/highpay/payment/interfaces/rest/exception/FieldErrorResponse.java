package com.highpay.payment.interfaces.rest.exception;

public record FieldErrorResponse(
        String field,
        String message
) {
}