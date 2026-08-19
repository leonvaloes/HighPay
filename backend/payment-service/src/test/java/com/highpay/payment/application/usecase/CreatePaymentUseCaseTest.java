package com.highpay.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.highpay.payment.application.port.OutboxEventRepository;
import com.highpay.payment.application.port.PaymentMetrics;
import com.highpay.payment.application.port.PaymentRepository;
import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.domain.model.Payment;

class CreatePaymentUseCaseTest {

    @Test
    void shouldCreatePaymentAndOutboxEventWhenIdempotencyKeyDoesNotExist() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryOutboxEventRepository outboxEventRepository = new InMemoryOutboxEventRepository();
        InMemoryPaymentMetrics paymentMetrics = new InMemoryPaymentMetrics();
        CreatePaymentUseCase useCase = new CreatePaymentUseCase(
                paymentRepository,
                outboxEventRepository,
                paymentMetrics);

        CreatePaymentResult result = useCase.execute(
                "idem-001",
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                PaymentMethod.PIX);

        assertThat(result.created()).isTrue();
        assertThat(result.payment().getIdempotencyKey()).isEqualTo("idem-001");
        assertThat(paymentRepository.savedPayments()).isEqualTo(1);
        assertThat(outboxEventRepository.savedPaymentCreatedEvents()).isEqualTo(1);
        assertThat(paymentMetrics.paymentCreated()).isEqualTo(1);
        assertThat(paymentMetrics.idempotencyHits()).isZero();
    }

    @Test
    void shouldReturnExistingPaymentWithoutCreatingAnotherOutboxEventWhenIdempotencyKeyAlreadyExists() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryOutboxEventRepository outboxEventRepository = new InMemoryOutboxEventRepository();
        InMemoryPaymentMetrics paymentMetrics = new InMemoryPaymentMetrics();
        CreatePaymentUseCase useCase = new CreatePaymentUseCase(
                paymentRepository,
                outboxEventRepository,
                paymentMetrics);

        CreatePaymentResult firstResult = useCase.execute(
                "idem-001",
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                PaymentMethod.PIX);

        CreatePaymentResult secondResult = useCase.execute(
                "idem-001",
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                PaymentMethod.PIX);

        assertThat(firstResult.created()).isTrue();
        assertThat(secondResult.created()).isFalse();
        assertThat(secondResult.payment().getId()).isEqualTo(firstResult.payment().getId());
        assertThat(paymentRepository.savedPayments()).isEqualTo(1);
        assertThat(outboxEventRepository.savedPaymentCreatedEvents()).isEqualTo(1);
        assertThat(paymentMetrics.paymentCreated()).isEqualTo(1);
        assertThat(paymentMetrics.idempotencyHits()).isEqualTo(1);
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentRequestPayload() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        InMemoryOutboxEventRepository outboxEventRepository = new InMemoryOutboxEventRepository();
        InMemoryPaymentMetrics paymentMetrics = new InMemoryPaymentMetrics();
        CreatePaymentUseCase useCase = new CreatePaymentUseCase(
                paymentRepository,
                outboxEventRepository,
                paymentMetrics);

        useCase.execute(
                "idem-001",
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                PaymentMethod.PIX);

        assertThatThrownBy(() -> useCase.execute(
                "idem-001",
                "merchant-001",
                new BigDecimal("200.00"),
                "BRL",
                PaymentMethod.PIX))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Idempotency key was already used with a different payment request");

        assertThat(paymentRepository.savedPayments()).isEqualTo(1);
        assertThat(outboxEventRepository.savedPaymentCreatedEvents()).isEqualTo(1);
        assertThat(paymentMetrics.paymentCreated()).isEqualTo(1);
        assertThat(paymentMetrics.idempotencyHits()).isZero();
    }
    private static class InMemoryPaymentRepository implements PaymentRepository {

        private final Map<String, Payment> paymentsByIdempotencyKey = new HashMap<>();
        private final Map<UUID, Payment> paymentsById = new HashMap<>();
        private int savedPayments;

        @Override
        public Optional<Payment> findById(UUID id) {
            return Optional.ofNullable(paymentsById.get(id));
        }

        @Override
        public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(paymentsByIdempotencyKey.get(idempotencyKey));
        }

        @Override
        public ListPaymentsResult findAll(int page, int size) {
            throw new UnsupportedOperationException("Not needed by this test");
        }

        @Override
        public Payment save(Payment payment) {
            savedPayments++;
            paymentsById.put(payment.getId(), payment);
            paymentsByIdempotencyKey.put(payment.getIdempotencyKey(), payment);
            return payment;
        }

        int savedPayments() {
            return savedPayments;
        }
    }

    private static class InMemoryOutboxEventRepository implements OutboxEventRepository {

        private int savedPaymentCreatedEvents;

        @Override
        public void savePaymentCreatedEvent(Payment payment) {
            savedPaymentCreatedEvents++;
        }

        int savedPaymentCreatedEvents() {
            return savedPaymentCreatedEvents;
        }
    }

    private static class InMemoryPaymentMetrics implements PaymentMetrics {

        private int paymentCreated;
        private int idempotencyHits;

        @Override
        public void recordPaymentCreated() {
            paymentCreated++;
        }

        @Override
        public void recordIdempotencyHit() {
            idempotencyHits++;
        }

        @Override
        public void recordPaymentProcessingStarted() {
        }

        @Override
        public void recordPaymentApproved() {
        }

        @Override
        public void recordPaymentRejected() {
        }

        @Override
        public void recordPaymentFailed() {
        }

        int paymentCreated() {
            return paymentCreated;
        }

        int idempotencyHits() {
            return idempotencyHits;
        }
    }
}