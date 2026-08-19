package com.highpay.processor.application.model;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCreatedEvent(
        UUID eventId,
        String correlationId,
        UUID paymentId,
        BigDecimal amount,
        String currency
) {
}
