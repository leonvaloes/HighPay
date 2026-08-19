package com.highpay.payment.infrastructure.messaging.outbox;

import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highpay.payment.application.port.OutboxMetrics;
import com.highpay.payment.infrastructure.messaging.rabbitmq.RabbitMqProperties;
import com.highpay.payment.infrastructure.observability.CorrelationId;
import com.highpay.payment.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.highpay.payment.infrastructure.persistence.outbox.OutboxEventEntity;
import com.highpay.payment.infrastructure.persistence.outbox.OutboxEventStatus;

@Component
@ConditionalOnProperty(
        name = "highpay.outbox.publisher.enabled",
        havingValue = "true")
public class OutboxPublisher {

    private final JpaOutboxEventRepository jpaOutboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties rabbitMqProperties;
    private final OutboxMetrics outboxMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int batchSize;
    private final int maxRetryAttempts;

    public OutboxPublisher(
            JpaOutboxEventRepository jpaOutboxEventRepository,
            RabbitTemplate rabbitTemplate,
            RabbitMqProperties rabbitMqProperties,
            OutboxMetrics outboxMetrics,
            @Value("${highpay.outbox.publisher.batch-size:20}") int batchSize,
            @Value("${highpay.outbox.publisher.max-retry-attempts:5}") int maxRetryAttempts) {

        this.jpaOutboxEventRepository = jpaOutboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMqProperties = rabbitMqProperties;
        this.outboxMetrics = outboxMetrics;
        this.batchSize = batchSize;
        this.maxRetryAttempts = maxRetryAttempts;
    }

    @Scheduled(fixedDelayString = "${highpay.outbox.publisher.fixed-delay-ms:5000}")
    public void publishPendingEventsOnSchedule() {
        publishPendingEvents();
    }

    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pendingEvents = jpaOutboxEventRepository.findPendingForPublishing(batchSize);

        for (OutboxEventEntity event : pendingEvents) {
            publish(event);
        }
    }

    private void publish(OutboxEventEntity event) {
        try {
            rabbitTemplate.convertAndSend(
                    rabbitMqProperties.paymentExchange(),
                    rabbitMqProperties.paymentCreatedRoutingKey(),
                    event.getPayload(),
                    message -> {
                        message.getMessageProperties().setHeader(
                                CorrelationId.HEADER_NAME,
                                correlationIdFrom(event));
                        return message;
                    });

            event.markAsPublished();
            jpaOutboxEventRepository.save(event);
            outboxMetrics.recordOutboxEventPublished();
        } catch (RuntimeException exception) {
            event.markAsFailed(maxRetryAttempts);
            jpaOutboxEventRepository.save(event);
            outboxMetrics.recordOutboxEventFailed();
        }
    }

    private String correlationIdFrom(OutboxEventEntity event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            JsonNode correlationId = payload.get("correlationId");

            if (correlationId != null && !correlationId.asText().isBlank()) {
                return correlationId.asText();
            }
        } catch (Exception exception) {
            return event.getId().toString();
        }

        return event.getId().toString();
    }
}
