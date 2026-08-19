package com.highpay.payment.infrastructure.persistence.outbox;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highpay.payment.application.port.OutboxEventRepository;
import com.highpay.payment.domain.model.Payment;
import com.highpay.payment.infrastructure.observability.CorrelationId;

@Repository
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

    private static final String AGGREGATE_TYPE_PAYMENT = "Payment";
    private static final String EVENT_TYPE_PAYMENT_CREATED = "PaymentCreated";

    private final JpaOutboxEventRepository jpaOutboxEventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxEventRepositoryAdapter(JpaOutboxEventRepository jpaOutboxEventRepository) {
        this.jpaOutboxEventRepository = jpaOutboxEventRepository;
    }

    @Override
    public void savePaymentCreatedEvent(Payment payment) {
        UUID eventId = UUID.randomUUID();
        OutboxEventEntity outboxEvent = OutboxEventEntity.pending(
                eventId,
                payment.getId(),
                AGGREGATE_TYPE_PAYMENT,
                EVENT_TYPE_PAYMENT_CREATED,
                toPayload(eventId, payment));

        jpaOutboxEventRepository.save(outboxEvent);
    }

    private String toPayload(UUID eventId, Payment payment) {
        PaymentCreatedPayload payload = new PaymentCreatedPayload(
                eventId,
                CorrelationId.currentOrNew(),
                payment.getId(),
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod().name(),
                payment.getStatus().name(),
                payment.getCreatedAt().toString());

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize payment created event", exception);
        }
    }

    private record PaymentCreatedPayload(
            UUID eventId,
            String correlationId,
            UUID paymentId,
            String merchantId,
            BigDecimal amount,
            String currency,
            String paymentMethod,
            String status,
            String createdAt) {
    }
}
