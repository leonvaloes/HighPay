package com.highpay.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.highpay.payment.application.port.PaymentRepository;
import com.highpay.payment.domain.enums.PaymentMethod;
import com.highpay.payment.domain.model.Payment;

class GetPaymentUseCaseTest {

    @Test
    void shouldReturnPaymentWhenIdExists() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        GetPaymentUseCase useCase = new GetPaymentUseCase(paymentRepository);

        Payment payment = Payment.create(
                "merchant-001",
                new BigDecimal("100.00"),
                "BRL",
                PaymentMethod.PIX,
                "idem-001");
        paymentRepository.save(payment);

        Optional<Payment> result = useCase.execute(payment.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(payment.getId());
    }

    @Test
    void shouldReturnEmptyWhenIdDoesNotExist() {
        InMemoryPaymentRepository paymentRepository = new InMemoryPaymentRepository();
        GetPaymentUseCase useCase = new GetPaymentUseCase(paymentRepository);

        Optional<Payment> result = useCase.execute(UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    private static class InMemoryPaymentRepository implements PaymentRepository {

        private final Map<UUID, Payment> paymentsById = new HashMap<>();
        private final Map<String, Payment> paymentsByIdempotencyKey = new HashMap<>();

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
            paymentsById.put(payment.getId(), payment);
            paymentsByIdempotencyKey.put(payment.getIdempotencyKey(), payment);
            return payment;
        }
    }
}