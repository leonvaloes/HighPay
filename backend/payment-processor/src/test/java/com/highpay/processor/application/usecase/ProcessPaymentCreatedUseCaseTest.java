package com.highpay.processor.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.highpay.processor.application.model.PaymentCreatedEvent;
import com.highpay.processor.application.model.ProviderPaymentResult;
import com.highpay.processor.application.port.PaymentProcessorMetrics;
import com.highpay.processor.application.port.PaymentServiceClient;
import com.highpay.processor.application.port.ProcessedEventRepository;
import com.highpay.processor.application.port.ProviderClient;

class ProcessPaymentCreatedUseCaseTest {

    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PAYLOAD = "{\"eventId\":\"22222222-2222-2222-2222-222222222222\",\"paymentId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":100.00,\"currency\":\"BRL\"}";

    @Test
    void shouldApprovePaymentWhenProviderReturnsSuccess() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        when(providerClient.process(any(PaymentCreatedEvent.class)))
                .thenReturn(new ProviderPaymentResult("SUCCESS", "provider-123"));

        useCase.execute(PAYLOAD);

        verify(paymentServiceClient).markAsProcessing(PAYMENT_ID);
        verify(paymentServiceClient).approve(PAYMENT_ID, "provider-123");
        assertThat(processedEventRepository.statusOf(EVENT_ID)).isEqualTo(EventStatus.PROCESSED);
        assertThat(metrics.consumedEvents).isEqualTo(1);
        assertThat(metrics.providerApproved).isEqualTo(1);
    }

    @Test
    void shouldRejectPaymentWhenProviderReturnsRejected() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        when(providerClient.process(any(PaymentCreatedEvent.class)))
                .thenReturn(new ProviderPaymentResult("REJECTED", "provider-456"));

        useCase.execute(PAYLOAD);

        verify(paymentServiceClient).markAsProcessing(PAYMENT_ID);
        verify(paymentServiceClient).reject(PAYMENT_ID, "provider-456");
        assertThat(processedEventRepository.statusOf(EVENT_ID)).isEqualTo(EventStatus.PROCESSED);
        assertThat(metrics.consumedEvents).isEqualTo(1);
        assertThat(metrics.providerRejected).isEqualTo(1);
    }
    @Test
    void shouldParsePaymentCreatedEventWithDifferentJsonFieldOrder() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        when(providerClient.process(any(PaymentCreatedEvent.class)))
                .thenReturn(new ProviderPaymentResult("SUCCESS", "provider-789"));
        String payload = "{ \"status\" : \"CREATED\", \"currency\" : \"BRL\", \"amount\" : 100.00, \"merchantId\" : \"merchant-1\", \"paymentId\" : \"11111111-1111-1111-1111-111111111111\", \"eventId\" : \"22222222-2222-2222-2222-222222222222\" }";

        useCase.execute(payload);

        verify(paymentServiceClient).markAsProcessing(PAYMENT_ID);
        verify(paymentServiceClient).approve(PAYMENT_ID, "provider-789");
        assertThat(processedEventRepository.statusOf(EVENT_ID)).isEqualTo(EventStatus.PROCESSED);
    }

    @Test
    void shouldMarkEventAsFailedWhenProviderThrowsException() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        when(providerClient.process(any(PaymentCreatedEvent.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(() -> useCase.execute(PAYLOAD))
                .isInstanceOf(IllegalStateException.class);

        verify(paymentServiceClient).markAsProcessing(PAYMENT_ID);
        verify(paymentServiceClient).fail(PAYMENT_ID);
        assertThat(processedEventRepository.statusOf(EVENT_ID)).isEqualTo(EventStatus.FAILED);
        assertThat(metrics.consumedEvents).isEqualTo(1);
        assertThat(metrics.processingFailed).isEqualTo(1);
    }

    @Test
    void shouldNotFailPaymentWhenProviderApprovedButApproveNotificationFails() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        IllegalStateException approveException = new IllegalStateException("payment-service unavailable");
        when(providerClient.process(any(PaymentCreatedEvent.class)))
                .thenReturn(new ProviderPaymentResult("SUCCESS", "provider-123"));
        doThrow(approveException)
                .when(paymentServiceClient)
                .approve(PAYMENT_ID, "provider-123");

        assertThatThrownBy(() -> useCase.execute(PAYLOAD))
                .isSameAs(approveException);

        verify(paymentServiceClient).markAsProcessing(PAYMENT_ID);
        verify(paymentServiceClient).approve(PAYMENT_ID, "provider-123");
        verify(paymentServiceClient, never()).fail(PAYMENT_ID);
        assertThat(processedEventRepository.statusOf(EVENT_ID)).isEqualTo(EventStatus.FAILED);
        assertThat(metrics.processingFailed).isEqualTo(1);
        assertThat(metrics.providerApproved).isZero();
    }

    @Test
    void shouldNotFailPaymentWhenProviderRejectedButRejectNotificationFails() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        IllegalStateException rejectException = new IllegalStateException("payment-service unavailable");
        when(providerClient.process(any(PaymentCreatedEvent.class)))
                .thenReturn(new ProviderPaymentResult("REJECTED", "provider-456"));
        doThrow(rejectException)
                .when(paymentServiceClient)
                .reject(PAYMENT_ID, "provider-456");

        assertThatThrownBy(() -> useCase.execute(PAYLOAD))
                .isSameAs(rejectException);

        verify(paymentServiceClient).markAsProcessing(PAYMENT_ID);
        verify(paymentServiceClient).reject(PAYMENT_ID, "provider-456");
        verify(paymentServiceClient, never()).fail(PAYMENT_ID);
        assertThat(processedEventRepository.statusOf(EVENT_ID)).isEqualTo(EventStatus.FAILED);
        assertThat(metrics.processingFailed).isEqualTo(1);
        assertThat(metrics.providerRejected).isZero();
    }
    @Test
    void shouldPreserveOriginalExceptionWhenFailNotificationAlsoFails() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        IllegalStateException providerException = new IllegalStateException("provider unavailable");
        IllegalStateException failNotificationException = new IllegalStateException("payment-service unavailable");
        when(providerClient.process(any(PaymentCreatedEvent.class)))
                .thenThrow(providerException);
        doThrow(failNotificationException)
                .when(paymentServiceClient)
                .fail(PAYMENT_ID);

        assertThatThrownBy(() -> useCase.execute(PAYLOAD))
                .isSameAs(providerException)
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .containsExactly(failNotificationException));

        assertThat(processedEventRepository.statusOf(EVENT_ID)).isEqualTo(EventStatus.FAILED);
        assertThat(metrics.processingFailed).isEqualTo(1);
        assertThat(metrics.paymentFailNotificationFailed).isEqualTo(1);
    }
    @Test
    void shouldSkipAlreadyProcessedDuplicateEvent() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        processedEventRepository.forceStatus(EVENT_ID, EventStatus.PROCESSED);
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);

        useCase.execute(PAYLOAD);

        verify(paymentServiceClient, never()).markAsProcessing(any(UUID.class));
        verify(providerClient, never()).process(any(PaymentCreatedEvent.class));
        assertThat(metrics.consumedEvents).isEqualTo(1);
        assertThat(metrics.duplicateEvents).isEqualTo(1);
    }

    @Test
    void shouldRetryFailedEvent() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        processedEventRepository.forceStatus(EVENT_ID, EventStatus.FAILED);
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        when(providerClient.process(any(PaymentCreatedEvent.class)))
                .thenReturn(new ProviderPaymentResult("SUCCESS", "provider-789"));

        useCase.execute(PAYLOAD);

        verify(paymentServiceClient).markAsProcessing(PAYMENT_ID);
        verify(paymentServiceClient).approve(PAYMENT_ID, "provider-789");
        assertThat(processedEventRepository.statusOf(EVENT_ID)).isEqualTo(EventStatus.PROCESSED);
    }

    @Test
    void shouldReuseStoredProviderDecisionWhenRetryingFailedEvent() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        processedEventRepository.forceStatus(EVENT_ID, EventStatus.FAILED);
        processedEventRepository.saveProviderResult(EVENT_ID, new ProviderPaymentResult("SUCCESS", "provider-stored"));
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);

        useCase.execute(PAYLOAD);

        verify(providerClient, never()).process(any(PaymentCreatedEvent.class));
        verify(paymentServiceClient, never()).markAsProcessing(PAYMENT_ID);
        verify(paymentServiceClient).approve(PAYMENT_ID, "provider-stored");
        assertThat(processedEventRepository.statusOf(EVENT_ID)).isEqualTo(EventStatus.PROCESSED);
    }
    @Test
    void shouldRejectPayloadMissingEventId() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        String payload = "{\"paymentId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":100.00,\"currency\":\"BRL\"}";

        assertThatThrownBy(() -> useCase.execute(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing field in payment created event: eventId");

        verify(paymentServiceClient, never()).markAsProcessing(any(UUID.class));
        verify(providerClient, never()).process(any(PaymentCreatedEvent.class));
        assertThat(metrics.consumedEvents).isZero();
    }

    @Test
    void shouldRejectPayloadWithInvalidAmount() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);
        String payload = "{\"eventId\":\"22222222-2222-2222-2222-222222222222\",\"paymentId\":\"11111111-1111-1111-1111-111111111111\",\"amount\":0,\"currency\":\"BRL\"}";

        assertThatThrownBy(() -> useCase.execute(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid field in payment created event: amount");

        verify(paymentServiceClient, never()).markAsProcessing(any(UUID.class));
        verify(providerClient, never()).process(any(PaymentCreatedEvent.class));
        assertThat(metrics.consumedEvents).isZero();
    }
    @Test
    void shouldRejectBlankPayload() {
        PaymentServiceClient paymentServiceClient = mock(PaymentServiceClient.class);
        ProviderClient providerClient = mock(ProviderClient.class);
        InMemoryProcessedEventRepository processedEventRepository = new InMemoryProcessedEventRepository();
        InMemoryPaymentProcessorMetrics metrics = new InMemoryPaymentProcessorMetrics();
        ProcessPaymentCreatedUseCase useCase = new ProcessPaymentCreatedUseCase(
                paymentServiceClient,
                providerClient,
                processedEventRepository,
                metrics);

        assertThatThrownBy(() -> useCase.execute(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment created event payload is required");
    }

    private enum EventStatus {
        PROCESSING,
        PROCESSED,
        FAILED
    }

    private static class InMemoryProcessedEventRepository implements ProcessedEventRepository {

        private final Map<UUID, EventStatus> statusesByEventId = new HashMap<>();
        private final Map<UUID, ProviderPaymentResult> providerResultsByEventId = new HashMap<>();

        @Override
        public boolean tryStartProcessing(UUID eventId, UUID paymentId) {
            EventStatus status = statusesByEventId.get(eventId);

            if (status == EventStatus.PROCESSING || status == EventStatus.PROCESSED) {
                return false;
            }

            statusesByEventId.put(eventId, EventStatus.PROCESSING);
            return true;
        }

        @Override
        public Optional<ProviderPaymentResult> findProviderResult(UUID eventId) {
            return Optional.ofNullable(providerResultsByEventId.get(eventId));
        }

        @Override
        public void saveProviderResult(UUID eventId, ProviderPaymentResult result) {
            providerResultsByEventId.put(eventId, result);
        }

        @Override
        public void markAsProcessed(UUID eventId) {
            statusesByEventId.put(eventId, EventStatus.PROCESSED);
        }

        @Override
        public void markAsFailed(UUID eventId) {
            statusesByEventId.put(eventId, EventStatus.FAILED);
        }

        void forceStatus(UUID eventId, EventStatus status) {
            statusesByEventId.put(eventId, status);
        }

        EventStatus statusOf(UUID eventId) {
            return statusesByEventId.get(eventId);
        }
    }

    private static class InMemoryPaymentProcessorMetrics implements PaymentProcessorMetrics {

        private int consumedEvents;
        private int duplicateEvents;
        private int providerApproved;
        private int providerRejected;
        private int providerFailed;
        private int processingFailed;
        private int paymentFailNotificationFailed;

        @Override
        public void recordPaymentCreatedEventConsumed() {
            consumedEvents++;
        }

        @Override
        public void recordDuplicateEventSkipped() {
            duplicateEvents++;
        }

        @Override
        public void recordProviderApproved() {
            providerApproved++;
        }

        @Override
        public void recordProviderRejected() {
            providerRejected++;
        }

        @Override
        public void recordProviderFailed() {
            providerFailed++;
        }

        @Override
        public void recordProcessingFailed() {
            processingFailed++;
        }

        @Override
        public void recordPaymentFailNotificationFailed() {
            paymentFailNotificationFailed++;
        }
    }
}