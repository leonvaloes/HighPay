package com.highpay.payment.infrastructure.observability;
import org.springframework.stereotype.Component;
import com.highpay.payment.application.port.OutboxMetrics;
import com.highpay.payment.infrastructure.persistence.outbox.JpaOutboxEventRepository;
import com.highpay.payment.infrastructure.persistence.outbox.OutboxEventStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
@Component
public class MicrometerOutboxMetrics implements OutboxMetrics {
    private final Counter outboxEventPublishedCounter;
    private final Counter outboxEventFailedCounter;
    public MicrometerOutboxMetrics(
            MeterRegistry meterRegistry,
            JpaOutboxEventRepository jpaOutboxEventRepository) {
        this.outboxEventPublishedCounter = Counter.builder("highpay_outbox_event_published_total")
                .description("Total outbox events published to RabbitMQ")
                .register(meterRegistry);
        this.outboxEventFailedCounter = Counter.builder("highpay_outbox_event_failed_total")
                .description("Total outbox events that failed publishing to RabbitMQ")
                .register(meterRegistry);
        Gauge.builder(
                "highpay_outbox_event_pending",
                jpaOutboxEventRepository,
                repository -> repository.countByStatus(OutboxEventStatus.PENDING))
                .description("Current pending outbox events")
                .register(meterRegistry);
    }
    @Override
    public void recordOutboxEventPublished() {
        outboxEventPublishedCounter.increment();
    }
    @Override
    public void recordOutboxEventFailed() {
        outboxEventFailedCounter.increment();
    }
}