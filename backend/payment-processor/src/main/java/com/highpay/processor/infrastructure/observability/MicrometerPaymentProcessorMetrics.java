package com.highpay.processor.infrastructure.observability;

import org.springframework.stereotype.Component;

import com.highpay.processor.application.port.PaymentProcessorMetrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MicrometerPaymentProcessorMetrics implements PaymentProcessorMetrics {

    private final Counter paymentCreatedEventConsumedCounter;
    private final Counter duplicateEventSkippedCounter;
    private final Counter providerApprovedCounter;
    private final Counter providerRejectedCounter;
    private final Counter providerFailedCounter;
    private final Counter processingFailedCounter;
    private final Counter paymentFailNotificationFailedCounter;

    public MicrometerPaymentProcessorMetrics(MeterRegistry meterRegistry) {
        this.paymentCreatedEventConsumedCounter = Counter.builder("highpay_processor_payment_created_event_consumed_total")
                .description("Total PaymentCreated events consumed by the processor")
                .register(meterRegistry);
        this.duplicateEventSkippedCounter = Counter.builder("highpay_processor_duplicate_event_skipped_total")
                .description("Total duplicate events skipped by the processor")
                .register(meterRegistry);
        this.providerApprovedCounter = Counter.builder("highpay_processor_provider_approved_total")
                .description("Total provider approvals handled by the processor")
                .register(meterRegistry);
        this.providerRejectedCounter = Counter.builder("highpay_processor_provider_rejected_total")
                .description("Total provider rejections handled by the processor")
                .register(meterRegistry);
        this.providerFailedCounter = Counter.builder("highpay_processor_provider_failed_total")
                .description("Total provider errors handled by the processor")
                .register(meterRegistry);
        this.processingFailedCounter = Counter.builder("highpay_processor_processing_failed_total")
                .description("Total processor executions that failed with exception")
                .register(meterRegistry);
        this.paymentFailNotificationFailedCounter = Counter.builder("highpay_processor_payment_fail_notification_failed_total")
                .description("Total failures when notifying payment-service that a payment failed")
                .register(meterRegistry);
    }

    @Override
    public void recordPaymentCreatedEventConsumed() {
        paymentCreatedEventConsumedCounter.increment();
    }

    @Override
    public void recordDuplicateEventSkipped() {
        duplicateEventSkippedCounter.increment();
    }

    @Override
    public void recordProviderApproved() {
        providerApprovedCounter.increment();
    }

    @Override
    public void recordProviderRejected() {
        providerRejectedCounter.increment();
    }

    @Override
    public void recordProviderFailed() {
        providerFailedCounter.increment();
    }

    @Override
    public void recordProcessingFailed() {
        processingFailedCounter.increment();
    }

    @Override
    public void recordPaymentFailNotificationFailed() {
        paymentFailNotificationFailedCounter.increment();
    }
}