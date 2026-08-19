package com.highpay.payment.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.highpay.payment.application.port.OutboxMetrics;
import com.highpay.payment.infrastructure.messaging.rabbitmq.RabbitMqProperties;
import com.highpay.payment.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.highpay.payment.infrastructure.persistence.outbox.OutboxEventEntity;
import com.highpay.payment.infrastructure.persistence.outbox.OutboxEventStatus;

class OutboxPublisherTest {

    @Test
    void shouldPublishPendingEventAndMarkAsPublished() {
        JpaOutboxEventRepository repository = mock(JpaOutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitMqProperties rabbitMqProperties = rabbitMqProperties();
        InMemoryOutboxMetrics outboxMetrics = new InMemoryOutboxMetrics();
        OutboxPublisher publisher = new OutboxPublisher(
                repository,
                rabbitTemplate,
                rabbitMqProperties,
                outboxMetrics,
                20,
                5);
        OutboxEventEntity event = pendingEvent();
        when(repository.findPendingForPublishing(20))
                .thenReturn(List.of(event));

        publisher.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(
                eq("highpay.payments.exchange"),
                eq("payment.created"),
                eq("{\"paymentId\":\"123\"}"),
                any(MessagePostProcessor.class));
        verify(repository).save(event);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(outboxMetrics.publishedEvents).isEqualTo(1);
    }

    @Test
    void shouldKeepEventPendingWhenPublishFailsBeforeMaxRetries() {
        JpaOutboxEventRepository repository = mock(JpaOutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        InMemoryOutboxMetrics outboxMetrics = new InMemoryOutboxMetrics();
        OutboxPublisher publisher = new OutboxPublisher(
                repository,
                rabbitTemplate,
                rabbitMqProperties(),
                outboxMetrics,
                20,
                5);
        OutboxEventEntity event = pendingEvent();
        when(repository.findPendingForPublishing(20))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("rabbit unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(
                        eq("highpay.payments.exchange"),
                        eq("payment.created"),
                        eq("{\"paymentId\":\"123\"}"),
                        any(MessagePostProcessor.class));

        publisher.publishPendingEvents();

        verify(repository).save(event);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(outboxMetrics.failedEvents).isEqualTo(1);
    }

    @Test
    void shouldMarkEventAsFailedWhenPublishReachesMaxRetries() {
        JpaOutboxEventRepository repository = mock(JpaOutboxEventRepository.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        InMemoryOutboxMetrics outboxMetrics = new InMemoryOutboxMetrics();
        OutboxPublisher publisher = new OutboxPublisher(
                repository,
                rabbitTemplate,
                rabbitMqProperties(),
                outboxMetrics,
                20,
                1);
        OutboxEventEntity event = pendingEvent();
        when(repository.findPendingForPublishing(20))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("rabbit unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(
                        eq("highpay.payments.exchange"),
                        eq("payment.created"),
                        eq("{\"paymentId\":\"123\"}"),
                        any(MessagePostProcessor.class));

        publisher.publishPendingEvents();

        verify(repository).save(event);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(outboxMetrics.failedEvents).isEqualTo(1);
    }

    private static OutboxEventEntity pendingEvent() {
        return OutboxEventEntity.pending(
                java.util.UUID.randomUUID(),
                "Payment",
                "PaymentCreated",
                "{\"paymentId\":\"123\"}");
    }

    private static RabbitMqProperties rabbitMqProperties() {
        return new RabbitMqProperties(
                "highpay.payments.exchange",
                "payment.created",
                "highpay.payment-created.queue",
                "highpay.payments.dlx",
                "payment.created.dead-letter",
                "highpay.payment-created.dlq");
    }

    private static class InMemoryOutboxMetrics implements OutboxMetrics {

        private int publishedEvents;
        private int failedEvents;

        @Override
        public void recordOutboxEventPublished() {
            publishedEvents++;
        }

        @Override
        public void recordOutboxEventFailed() {
            failedEvents++;
        }
    }
}
