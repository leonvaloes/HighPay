package com.highpay.payment.infrastructure.observability;

import org.springframework.stereotype.Component;

import com.highpay.payment.application.port.PaymentMetrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MicrometerPaymentMetrics implements PaymentMetrics {

    private final Counter paymentCreatedCounter;
    private final Counter idempotencyHitCounter;
    private final Counter paymentProcessingStartedCounter;
    private final Counter paymentApprovedCounter;
    private final Counter paymentRejectedCounter;
    private final Counter paymentFailedCounter;

    public MicrometerPaymentMetrics(MeterRegistry meterRegistry) {
        this.paymentCreatedCounter = Counter.builder("highpay_payment_created_total")
                .description("Total payments created")
                .register(meterRegistry);
        this.idempotencyHitCounter = Counter.builder("highpay_payment_idempotency_hit_total")
                .description("Total idempotent payment creation hits")
                .register(meterRegistry);
        this.paymentProcessingStartedCounter = Counter.builder("highpay_payment_processing_started_total")
                .description("Total payments moved to processing")
                .register(meterRegistry);
        this.paymentApprovedCounter = Counter.builder("highpay_payment_approved_total")
                .description("Total payments approved")
                .register(meterRegistry);
        this.paymentRejectedCounter = Counter.builder("highpay_payment_rejected_total")
                .description("Total payments rejected")
                .register(meterRegistry);
        this.paymentFailedCounter = Counter.builder("highpay_payment_failed_total")
                .description("Total payments failed")
                .register(meterRegistry);
    }

    @Override
    public void recordPaymentCreated() {
        paymentCreatedCounter.increment();
    }

    @Override
    public void recordIdempotencyHit() {
        idempotencyHitCounter.increment();
    }

    @Override
    public void recordPaymentProcessingStarted() {
        paymentProcessingStartedCounter.increment();
    }

    @Override
    public void recordPaymentApproved() {
        paymentApprovedCounter.increment();
    }

    @Override
    public void recordPaymentRejected() {
        paymentRejectedCounter.increment();
    }

    @Override
    public void recordPaymentFailed() {
        paymentFailedCounter.increment();
    }
}