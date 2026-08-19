package com.highpay.processor.application.usecase;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highpay.processor.application.model.PaymentCreatedEvent;
import com.highpay.processor.application.model.ProviderPaymentResult;
import com.highpay.processor.application.port.PaymentProcessorMetrics;
import com.highpay.processor.application.port.PaymentServiceClient;
import com.highpay.processor.application.port.ProcessedEventRepository;
import com.highpay.processor.application.port.ProviderClient;

@Service
public class ProcessPaymentCreatedUseCase {

    private final PaymentServiceClient paymentServiceClient;
    private final ProviderClient providerClient;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentProcessorMetrics paymentProcessorMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcessPaymentCreatedUseCase(
            PaymentServiceClient paymentServiceClient,
            ProviderClient providerClient,
            ProcessedEventRepository processedEventRepository,
            PaymentProcessorMetrics paymentProcessorMetrics) {

        this.paymentServiceClient = paymentServiceClient;
        this.providerClient = providerClient;
        this.processedEventRepository = processedEventRepository;
        this.paymentProcessorMetrics = paymentProcessorMetrics;
    }

    public void execute(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Payment created event payload is required");
        }

        PaymentCreatedEvent event = parse(payload);
        paymentProcessorMetrics.recordPaymentCreatedEventConsumed();

        if (!processedEventRepository.tryStartProcessing(event.eventId(), event.paymentId())) {
            paymentProcessorMetrics.recordDuplicateEventSkipped();
            return;
        }

        ProviderPaymentResult result = getOrRequestProviderDecision(event);
        applyProviderDecision(event, result);
    }

    private ProviderPaymentResult getOrRequestProviderDecision(PaymentCreatedEvent event) {
        Optional<ProviderPaymentResult> storedResult = processedEventRepository.findProviderResult(event.eventId());

        if (storedResult.isPresent()) {
            return storedResult.get();
        }

        try {
            paymentServiceClient.markAsProcessing(event.paymentId());
            ProviderPaymentResult result = providerClient.process(event);
            processedEventRepository.saveProviderResult(event.eventId(), result);
            return result;
        } catch (RuntimeException exception) {
            processedEventRepository.markAsFailed(event.eventId());
            notifyPaymentFailure(event.paymentId(), exception);
            paymentProcessorMetrics.recordProcessingFailed();
            throw exception;
        }
    }

    private void applyProviderDecision(PaymentCreatedEvent event, ProviderPaymentResult result) {
        if (result.approved()) {
            approvePayment(event, result.providerTransactionId());
            return;
        }

        if (result.rejected()) {
            rejectPayment(event, result.providerTransactionId());
            return;
        }

        failPaymentAfterProviderError(event);
    }

    private void approvePayment(PaymentCreatedEvent event, String providerTransactionId) {
        try {
            paymentServiceClient.approve(event.paymentId(), providerTransactionId);
            processedEventRepository.markAsProcessed(event.eventId());
            paymentProcessorMetrics.recordProviderApproved();
        } catch (RuntimeException exception) {
            processedEventRepository.markAsFailed(event.eventId());
            paymentProcessorMetrics.recordProcessingFailed();
            throw exception;
        }
    }

    private void rejectPayment(PaymentCreatedEvent event, String providerTransactionId) {
        try {
            paymentServiceClient.reject(event.paymentId(), providerTransactionId);
            processedEventRepository.markAsProcessed(event.eventId());
            paymentProcessorMetrics.recordProviderRejected();
        } catch (RuntimeException exception) {
            processedEventRepository.markAsFailed(event.eventId());
            paymentProcessorMetrics.recordProcessingFailed();
            throw exception;
        }
    }

    private void failPaymentAfterProviderError(PaymentCreatedEvent event) {
        try {
            paymentServiceClient.fail(event.paymentId());
            processedEventRepository.markAsProcessed(event.eventId());
            paymentProcessorMetrics.recordProviderFailed();
        } catch (RuntimeException exception) {
            processedEventRepository.markAsFailed(event.eventId());
            paymentProcessorMetrics.recordPaymentFailNotificationFailed();
            paymentProcessorMetrics.recordProcessingFailed();
            throw exception;
        }
    }

    private void notifyPaymentFailure(UUID paymentId, RuntimeException originalException) {
        try {
            paymentServiceClient.fail(paymentId);
        } catch (RuntimeException notificationException) {
            paymentProcessorMetrics.recordPaymentFailNotificationFailed();
            originalException.addSuppressed(notificationException);
        }
    }

    private PaymentCreatedEvent parse(String payload) {
        try {
            PaymentCreatedEvent event = objectMapper.readValue(payload, PaymentCreatedEvent.class);
            validate(event);
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid payment created event payload", exception);
        }
    }

    private void validate(PaymentCreatedEvent event) {
        if (event.eventId() == null) {
            throw new IllegalArgumentException("Missing field in payment created event: eventId");
        }
        if (event.paymentId() == null) {
            throw new IllegalArgumentException("Missing field in payment created event: paymentId");
        }
        if (event.amount() == null) {
            throw new IllegalArgumentException("Missing field in payment created event: amount");
        }
        if (event.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid field in payment created event: amount");
        }
        if (event.currency() == null || event.currency().isBlank()) {
            throw new IllegalArgumentException("Missing field in payment created event: currency");
        }
    }
}