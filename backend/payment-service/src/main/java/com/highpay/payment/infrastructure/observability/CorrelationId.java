package com.highpay.payment.infrastructure.observability;

import java.util.UUID;

import org.slf4j.MDC;

public final class CorrelationId {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String currentOrNew() {
        String current = MDC.get(MDC_KEY);

        if (current == null || current.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return current;
    }

    public static String normalize(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return correlationId.trim();
    }
}
